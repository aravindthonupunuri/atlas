package com.tgt.lists.atlas.api.async


import com.tgt.lists.cart.transport.AddMultiCartItemsRequest
import com.tgt.lists.cart.transport.DeleteMultiCartItemsRequest
import com.tgt.lists.cart.transport.UpdateMultiCartItemsRequest
import com.tgt.lists.atlas.api.domain.*
import com.tgt.lists.atlas.api.transport.ListItemMetaDataTO
import com.tgt.lists.atlas.api.transport.UserItemMetaDataTO
import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.kafka.model.CreateListItemNotifyEvent
import com.tgt.lists.atlas.kafka.model.DeleteListItemNotifyEvent
import com.tgt.lists.atlas.kafka.model.UpdateListItemNotifyEvent
import com.tgt.lists.atlas.util.CartDataProvider
import com.tgt.lists.atlas.util.TestListChannel
import org.apache.kafka.clients.producer.RecordMetadata
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.LocalDateTime

class PendingToCompletedItemStateServiceTest extends Specification {
    
    CartManager cartManager
    EventPublisher eventPublisher
    AddMultiItemsManager addMultiItemsManager
    DeduplicationManager deduplicationManager
    DeleteCartItemsManager deleteCartItemsManager
    CreateCartItemsManager createCartItemsManager
    UpdateCartItemsManager updateCartItemsManager
    PendingToCompletedItemStateService pendingToCompletedItemStateService
    CartDataProvider cartDataProvider
    String guestId = "1234"
    Long locationId = 1375L

    def setup() {
        cartManager = Mock(CartManager)
        eventPublisher = Mock(EventPublisher)
        deleteCartItemsManager = new DeleteCartItemsManager(cartManager, eventPublisher, true)
        createCartItemsManager = new CreateCartItemsManager(cartManager, eventPublisher)
        updateCartItemsManager = new UpdateCartItemsManager(cartManager, eventPublisher)
        deduplicationManager = new DeduplicationManager(cartManager, updateCartItemsManager, deleteCartItemsManager, true, 5, 5, false)
        addMultiItemsManager = new AddMultiItemsManager(deduplicationManager, createCartItemsManager)
        pendingToCompletedItemStateService = new PendingToCompletedItemStateService(cartManager, deleteCartItemsManager, addMultiItemsManager)
        cartDataProvider = new CartDataProvider()
    }

    def "test processCompletionItemEvent() integrity"() {
        given:
        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def itemId1 = UUID.randomUUID()
        def itemId2 = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "5678"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)
        def itemIds = [itemId1, itemId2]
        PendingToCompletedItemStateService.RetryState processingState = new PendingToCompletedItemStateService.RetryState(false, false)
        def recordMetadata = GroovyMock(RecordMetadata)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, null)
        def pendingItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, itemId1 , tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, itemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 3, "notes2", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartContentResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])
        def completedItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, UUID.randomUUID() , tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 3, "notes3", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def completedCartContentResponse = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1])
        def dedupedCompletedCartItem =  cartDataProvider.getCartItemResponse(completedListId, completedCartItemResponse1.cartItemId , tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 6, "notes3\nnotes2", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def updateMultiCartItemsResponse = cartDataProvider.getCartContentsResponse(completedCartResponse, [dedupedCompletedCartItem])
        def addedCompletedCartItem = cartDataProvider.getCartItemResponse(completedListId, UUID.randomUUID() , tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartItemsDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(listId,
                [pendingCartItemResponse1.cartItemId, pendingCartItemResponse2.cartItemId], null)

        when:
        def actual = pendingToCompletedItemStateService.processPendingToCompletedItemState(guestId, locationId, listId, itemIds, processingState).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse) // get completed cart
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(pendingCartContentResponse) // call to get items in pending list
        1 * cartManager.getListCartContents(completedListId, true) >> Mono.just(completedCartContentResponse) //  dedup call
        1 * cartManager.updateMultiCartItems(_ as UpdateMultiCartItemsRequest) >> { arguments ->
            final UpdateMultiCartItemsRequest updateMultiCartItemsRequest = arguments[0]
            assert updateMultiCartItemsRequest.cartId == completedListId
            assert updateMultiCartItemsRequest.cartItems.size() == 1
            Mono.just(updateMultiCartItemsResponse)
        } // update deduped items
        1 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata) // update item in completed list notify event
        1 * cartManager.addMultiCartItems(_ as AddMultiCartItemsRequest, _)  >> { arguments ->  // add new items
            final AddMultiCartItemsRequest addMultiCartItemsRequest = arguments[0]
            assert addMultiCartItemsRequest.cartId == completedListId
            assert addMultiCartItemsRequest.cartItems.size() == 1
            assert addMultiCartItemsRequest.cartItems.first().cartId == completedListId
            Mono.just([addedCompletedCartItem])
        }
        1 * eventPublisher.publishEvent(CreateListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata) // create item in completed list notify event
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest) >> { arguments -> // delete items in pending list
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0] == pendingCartItemResponse1.cartItemId
            assert request.cartItemIds[1] == pendingCartItemResponse2.cartItemId
            Mono.just(pendingCartItemsDeleteResponse)
        }
        1 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata) // delete item in pending list notify event

        actual.createItemsInCompletedList
        actual.deleteItemsInPendingList
    }

    def "test processCompletionItemEvent() with no existing completed cart present"() {
        given:
        def listId = UUID.randomUUID()
        def itemId1 = UUID.randomUUID()
        def itemId2 = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "5678"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)
        def itemIds = [itemId1, itemId2]
        PendingToCompletedItemStateService.RetryState processingState = new PendingToCompletedItemStateService.RetryState(false, false)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def pendingItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, itemId1 , tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, itemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 3, "notes2", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartContentResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])

        when:
        def actual = pendingToCompletedItemStateService.processPendingToCompletedItemState(guestId, locationId, listId, itemIds, processingState).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.empty()// get completed cart
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(pendingCartContentResponse) // call to get items in pending list

        !actual.createItemsInCompletedList
        !actual.deleteItemsInPendingList
    }

    def "test processCompletionItemEvent() with exception getting completed cart"() {
        given:
        def listId = UUID.randomUUID()
        def itemId1 = UUID.randomUUID()
        def itemId2 = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "5678"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)
        def itemIds = [itemId1, itemId2]
        PendingToCompletedItemStateService.RetryState processingState = new PendingToCompletedItemStateService.RetryState(false, false)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def pendingItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, itemId1 , tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, itemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 3, "notes2", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartContentResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])

        when:
        def actual = pendingToCompletedItemStateService.processPendingToCompletedItemState(guestId, locationId, listId, itemIds, processingState).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.error(new RuntimeException())// get completed cart
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(pendingCartContentResponse) // call to get items in pending list

        !actual.createItemsInCompletedList
        !actual.deleteItemsInPendingList
    }

    def "test processCompletionItemEvent() with non existing cart items to update in pending list"() {
        given:
        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def itemId1 = UUID.randomUUID()
        def itemId2 = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "5678"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)
        def itemIds = [itemId1, itemId2]
        PendingToCompletedItemStateService.RetryState processingState = new PendingToCompletedItemStateService.RetryState(false, false)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, null)
        def pendingItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID() , tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 3, "notes2", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartContentResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])

        when:
        def actual = pendingToCompletedItemStateService.processPendingToCompletedItemState(guestId, locationId, listId, itemIds, processingState).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse) // get completed cart
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(pendingCartContentResponse) // call to get items in pending list

        !actual.createItemsInCompletedList
        !actual.deleteItemsInPendingList
    }

    def "test processCompletionItemEvent() with exception getting cart items in  pending list to update"() {
        given:
        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def itemId1 = UUID.randomUUID()
        def itemId2 = UUID.randomUUID()
        def itemIds = [itemId1, itemId2]
        PendingToCompletedItemStateService.RetryState processingState = new PendingToCompletedItemStateService.RetryState(false, false)
        def completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, null)

        when:
        def actual = pendingToCompletedItemStateService.processPendingToCompletedItemState(guestId, locationId, listId, itemIds, processingState).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse) // get completed cart
        1 * cartManager.getListCartContents(listId, true) >> Mono.error(new RuntimeException()) // call to get items in pending list

        !actual.createItemsInCompletedList
        !actual.deleteItemsInPendingList
    }

    def "test processCompletionItemEvent() with exception during dedup"() {
        given:
        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def itemId1 = UUID.randomUUID()
        def itemId2 = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "5678"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)
        def itemIds = [itemId1, itemId2]
        PendingToCompletedItemStateService.RetryState processingState = new PendingToCompletedItemStateService.RetryState(false, false)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, null)
        def pendingItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, itemId1 , tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, itemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 3, "notes2", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartContentResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])
        def completedItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, UUID.randomUUID() , tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 3, "notes3", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def completedCartContentResponse = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1])

        when:
        def actual = pendingToCompletedItemStateService.processPendingToCompletedItemState(guestId, locationId, listId, itemIds, processingState).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse) // get completed cart
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(pendingCartContentResponse) // call to get items in pending list
        1 * cartManager.getListCartContents(completedListId, true) >> Mono.just(completedCartContentResponse) //  dedup call
        1 * cartManager.updateMultiCartItems(_ as UpdateMultiCartItemsRequest) >> Mono.error(new RuntimeException())

        !actual.createItemsInCompletedList
        !actual.deleteItemsInPendingList
    }

    def "test processCompletionItemEvent() with exception during creating cart items in completed list"() {
        given:
        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def itemId1 = UUID.randomUUID()
        def itemId2 = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "5678"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)
        def itemIds = [itemId1, itemId2]
        PendingToCompletedItemStateService.RetryState processingState = new PendingToCompletedItemStateService.RetryState(false, false)
        def recordMetadata = GroovyMock(RecordMetadata)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, null)
        def pendingItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, itemId1 , tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, itemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 3, "notes2", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartContentResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])
        def completedItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, UUID.randomUUID() , tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 3, "notes3", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def completedCartContentResponse = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1])
        def dedupedCompletedCartItem =  cartDataProvider.getCartItemResponse(completedListId, completedCartItemResponse1.cartItemId , tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 6, "notes3\nnotes2", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def updateMultiCartItemsResponse = cartDataProvider.getCartContentsResponse(completedCartResponse, [dedupedCompletedCartItem])

        when:
        def actual = pendingToCompletedItemStateService.processPendingToCompletedItemState(guestId, locationId, listId, itemIds, processingState).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse) // get completed cart
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(pendingCartContentResponse) // call to get items in pending list
        1 * cartManager.getListCartContents(completedListId, true) >> Mono.just(completedCartContentResponse) //  dedup call
        1 * cartManager.updateMultiCartItems(_ as UpdateMultiCartItemsRequest) >> { arguments ->
            final UpdateMultiCartItemsRequest updateMultiCartItemsRequest = arguments[0]
            assert updateMultiCartItemsRequest.cartId == completedListId
            assert updateMultiCartItemsRequest.cartItems.size() == 1
            Mono.just(updateMultiCartItemsResponse)
        } // update deduped items
        1 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata) // update item in completed list notify event
        1 * cartManager.addMultiCartItems(_ as AddMultiCartItemsRequest, _)  >> Mono.error(new RuntimeException())

        !actual.createItemsInCompletedList
        !actual.deleteItemsInPendingList
    }

    def "test processCompletionItemEvent() with exception during deleting cart items from pending list"() {
        given:
        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def itemId1 = UUID.randomUUID()
        def itemId2 = UUID.randomUUID()

        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "5678"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)
        def itemIds = [itemId1, itemId2]
        PendingToCompletedItemStateService.RetryState processingState = new PendingToCompletedItemStateService.RetryState(false, false)
        def recordMetadata = GroovyMock(RecordMetadata)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, null)
        def pendingItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, itemId1 , tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, itemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 3, "notes2", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartContentResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])
        def completedItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, UUID.randomUUID() , tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 3, "notes3", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def completedCartContentResponse = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1])
        def dedupedCompletedCartItem =  cartDataProvider.getCartItemResponse(completedListId, completedCartItemResponse1.cartItemId , tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 6, "notes3\nnotes2", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def updateMultiCartItemsResponse = cartDataProvider.getCartContentsResponse(completedCartResponse, [dedupedCompletedCartItem])
        def addedCompletedCartItem = cartDataProvider.getCartItemResponse(completedListId, UUID.randomUUID() , tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def addCompletedCartContentsResponse = cartDataProvider.getCartContentsResponse(completedCartResponse, [dedupedCompletedCartItem, addedCompletedCartItem])

        when:
        def actual = pendingToCompletedItemStateService.processPendingToCompletedItemState(guestId, locationId, listId, itemIds, processingState).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse) // get completed cart
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(pendingCartContentResponse) // call to get items in pending list
        1 * cartManager.getListCartContents(completedListId, true) >> Mono.just(completedCartContentResponse) //  dedup call
        1 * cartManager.updateMultiCartItems(_ as UpdateMultiCartItemsRequest) >> { arguments ->
            final UpdateMultiCartItemsRequest updateMultiCartItemsRequest = arguments[0]
            assert updateMultiCartItemsRequest.cartId == completedListId
            assert updateMultiCartItemsRequest.cartItems.size() == 1
            Mono.just(updateMultiCartItemsResponse)
        } // update deduped items
        1 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata) // update item in completed list notify event
        1 * cartManager.addMultiCartItems(_ as AddMultiCartItemsRequest, _)  >> { arguments ->  // add new items
            final AddMultiCartItemsRequest addMultiCartItemsRequest = arguments[0]
            assert addMultiCartItemsRequest.cartId == completedListId
            assert addMultiCartItemsRequest.cartItems.size() == 1
            assert addMultiCartItemsRequest.cartItems.first().cartId == completedListId
            Mono.just([addedCompletedCartItem])
        }
        1 * eventPublisher.publishEvent(CreateListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata) // create item in completed list notify event
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest) >> Mono.error(new RuntimeException())

        actual.createItemsInCompletedList
        !actual.deleteItemsInPendingList
    }

    def "test processCompletionItemEvent() with no item to delete in pending list"() {
        given:
        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def itemId1 = UUID.randomUUID()
        def itemId2 = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "5678"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)
        def itemIds = [itemId1, itemId2]
        PendingToCompletedItemStateService.RetryState processingState = new PendingToCompletedItemStateService.RetryState(false, false)
        def recordMetadata = GroovyMock(RecordMetadata)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, null)
        def pendingItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, itemId1, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, itemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 3, "notes2", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartContentResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])
        def completedItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, UUID.randomUUID(), tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 3, "notes3", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def completedCartContentResponse = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1])
        def dedupedCompletedCartItem = cartDataProvider.getCartItemResponse(completedListId, completedCartItemResponse1.cartItemId, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 6, "notes3\nnotes2", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def updateMultiCartItemsResponse = cartDataProvider.getCartContentsResponse(completedCartResponse, [dedupedCompletedCartItem])
        def addedCompletedCartItem = cartDataProvider.getCartItemResponse(completedListId, UUID.randomUUID(), tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)

        when:
        def actual = pendingToCompletedItemStateService.processPendingToCompletedItemState(guestId, locationId, listId, itemIds, processingState).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse) // get completed cart
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(pendingCartContentResponse) // call to get items in pending list
        1 * cartManager.getListCartContents(completedListId, true) >> Mono.just(completedCartContentResponse) //  dedup call
        1 * cartManager.updateMultiCartItems(_ as UpdateMultiCartItemsRequest) >> { arguments ->
            final UpdateMultiCartItemsRequest updateMultiCartItemsRequest = arguments[0]
            assert updateMultiCartItemsRequest.cartId == completedListId
            assert updateMultiCartItemsRequest.cartItems.size() == 1
            Mono.just(updateMultiCartItemsResponse)
        } // update deduped items
        1 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _, listId.toString()) >> Mono.just(recordMetadata) // update item in completed list notify event
        1 * cartManager.addMultiCartItems(_ as AddMultiCartItemsRequest, _) >> { arguments ->  // add new items
            final AddMultiCartItemsRequest addMultiCartItemsRequest = arguments[0]
            assert addMultiCartItemsRequest.cartId == completedListId
            assert addMultiCartItemsRequest.cartItems.size() == 1
            assert addMultiCartItemsRequest.cartItems.first().cartId == completedListId
            Mono.just([addedCompletedCartItem])
        }
        1 * eventPublisher.publishEvent(CreateListItemNotifyEvent.getEventType(), _, listId.toString()) >> Mono.just(recordMetadata) // create item in completed list notify event
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest) >> Mono.empty()

        actual.createItemsInCompletedList
        actual.deleteItemsInPendingList
    }

    def "test processCompletionItemEvent() with partial state"() {
        given:
        def listId = UUID.randomUUID()
        def itemId1 = UUID.randomUUID()
        def itemId2 = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "5678"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)
        def itemIds = [itemId1, itemId2]
        PendingToCompletedItemStateService.RetryState processingState = new PendingToCompletedItemStateService.RetryState(true, false)
        def recordMetadata = GroovyMock(RecordMetadata)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId, null)

        def pendingItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, itemId1 , tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, itemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 3, "notes2", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartContentResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])
        def pendingCartItemsDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(listId,
                [pendingCartItemResponse1.cartItemId, pendingCartItemResponse2.cartItemId], null)

        when:
        def actual = pendingToCompletedItemStateService.processPendingToCompletedItemState(guestId, locationId, listId, itemIds, processingState).block()

        then:
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(pendingCartContentResponse) // call to get items in pending list
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest) >> { arguments -> // delete items in pending list
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0] == pendingCartItemResponse1.cartItemId
            assert request.cartItemIds[1] == pendingCartItemResponse2.cartItemId
            Mono.just(pendingCartItemsDeleteResponse)
        }
        1 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata) // delete item in pending list notify event

        actual.createItemsInCompletedList
        actual.deleteItemsInPendingList
    }

    def "test processCompletionItemEvent() with completed processing state"() {
        given:
        def listId = UUID.randomUUID()
        def itemId1 = UUID.randomUUID()
        def itemId2 = UUID.randomUUID()
        def itemIds = [itemId1, itemId2]
        PendingToCompletedItemStateService.RetryState processingState = new PendingToCompletedItemStateService.RetryState(true, true)

        when:
        def actual = pendingToCompletedItemStateService.processPendingToCompletedItemState(guestId, locationId, listId, itemIds, processingState).block()

        then:
        actual.createItemsInCompletedList
        actual.deleteItemsInPendingList
    }
}
