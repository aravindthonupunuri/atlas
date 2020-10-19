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

class CompletedToPendingItemStateServiceTest extends Specification {
    
    CartManager cartManager
    EventPublisher eventPublisher
    AddMultiItemsManager addMultiItemsManager
    DeduplicationManager deduplicationManager
    DeleteCartItemsManager deleteCartItemsManager
    CreateCartItemsManager createCartItemsManager
    UpdateCartItemsManager updateCartItemsManager
    CompletedToPendingItemStateService completedToPendingItemStateService
    CartDataProvider cartDataProvider
    String guestId = "1234"
    Long locationId = 1375L

    def setup() {
        cartManager = Mock(CartManager)
        eventPublisher = Mock(EventPublisher)
        deleteCartItemsManager = new DeleteCartItemsManager(cartManager, eventPublisher, true)
        updateCartItemsManager = new UpdateCartItemsManager(cartManager, eventPublisher)
        deduplicationManager = new DeduplicationManager(cartManager, updateCartItemsManager, deleteCartItemsManager, true, 5, 5, false)
        createCartItemsManager = new CreateCartItemsManager(cartManager, eventPublisher)
        addMultiItemsManager = new AddMultiItemsManager(deduplicationManager, createCartItemsManager)
        completedToPendingItemStateService = new CompletedToPendingItemStateService(cartManager, deleteCartItemsManager, addMultiItemsManager)
        cartDataProvider = new CartDataProvider()
    }

    def "test processPendingItemEvent() integrity"() {
        given:
        UUID itemId1 = UUID.randomUUID()
        UUID itemId2 = UUID.randomUUID()
        UUID listId = UUID.randomUUID()
        UUID completedListId = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "5678"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)
        def itemIds = [itemId1, itemId2]
        CompletedToPendingItemStateService.RetryState processingState = new CompletedToPendingItemStateService.RetryState(false, false)
        def recordMetadata = GroovyMock(RecordMetadata)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, null)
        def completedItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, itemId1, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def completedCartItemResponse2 = cartDataProvider.getCartItemResponse(completedListId, itemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 3, "notes2", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def completedCartContent = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1, completedCartItemResponse2])

        def pendingItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 3, "notes3", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartContent = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1])

        def dedupedPendingCartItem = cartDataProvider.getCartItemResponse(listId, pendingCartItemResponse1.cartItemId, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 6, "notes3\nnotes2", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def updateMultiCartItemsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [dedupedPendingCartItem])

        def addedPendingCartItem = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def completedCartItemsDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(completedListId,
                [completedCartItemResponse1.cartItemId, completedCartItemResponse2.cartItemId], null)

        when:
        def actual = completedToPendingItemStateService.processCompletedToPendingItemState(guestId, locationId, listId, itemIds, processingState).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse) // get completed cart
        1 * cartManager.getCompletedListCartContents(completedListId, true) >> Mono.just(completedCartContent) // get items in completed cart
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(pendingCartContent) //  dedup call
        1 * cartManager.updateMultiCartItems(_ as UpdateMultiCartItemsRequest) >> { arguments ->
            final UpdateMultiCartItemsRequest updateMultiCartItemsRequest = arguments[0]
            assert updateMultiCartItemsRequest.cartId == listId
            assert updateMultiCartItemsRequest.cartItems.size() == 1
            Mono.just(updateMultiCartItemsResponse)
        } // update deduped items
        1 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _, listId.toString()) >> Mono.just(recordMetadata) // update item in completed list notify event
        1 * cartManager.addMultiCartItems( _ as AddMultiCartItemsRequest, _) >> { arguments ->  // add new items
            final AddMultiCartItemsRequest addMultiCartItemsRequest = arguments[0]
            assert addMultiCartItemsRequest.cartId == listId
            assert addMultiCartItemsRequest.cartItems.size() == 1
            assert addMultiCartItemsRequest.cartItems.first().cartId == listId
            Mono.just([addedPendingCartItem])
        }
        1 * eventPublisher.publishEvent(CreateListItemNotifyEvent.getEventType(), _, listId.toString()) >> Mono.just(recordMetadata) // create item in completed list notify event
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest) >> { arguments -> // delete items in completed list
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartId == completedListId
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0] == completedCartItemResponse1.cartItemId
            assert request.cartItemIds[1] == completedCartItemResponse2.cartItemId
            Mono.just(completedCartItemsDeleteResponse)
        }
        1 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _, listId.toString()) >> Mono.just(recordMetadata) // delete item in pending list notify event

        actual.createItemsInPendingList
        actual.deleteItemsInCompletedList
    }

    def "test processPendingItemEvent() with error getting completed cart"() {
        given:
        UUID itemId1 = UUID.randomUUID()
        UUID itemId2 = UUID.randomUUID()
        UUID listId = UUID.randomUUID()
        def itemIds = [itemId1, itemId2]
        CompletedToPendingItemStateService.RetryState processingState = new CompletedToPendingItemStateService.RetryState(false, false)

        when:
        def actual = completedToPendingItemStateService.processCompletedToPendingItemState(guestId, locationId, listId, itemIds, processingState).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.error(new RuntimeException()) // get completed cart

        !actual.createItemsInPendingList
        !actual.deleteItemsInCompletedList
    }

    def "test processPendingItemEvent() with no completed cart present"() {
        given:
        UUID itemId1 = UUID.randomUUID()
        UUID itemId2 = UUID.randomUUID()
        UUID listId = UUID.randomUUID()
        def itemIds = [itemId1, itemId2]
        CompletedToPendingItemStateService.RetryState processingState = new CompletedToPendingItemStateService.RetryState(false, false)

        when:
        def actual = completedToPendingItemStateService.processCompletedToPendingItemState(guestId, locationId, listId, itemIds, processingState).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.empty() // get completed cart

        !actual.createItemsInPendingList
        !actual.deleteItemsInCompletedList
    }

    def "test processPendingItemEvent() with error getting items in completed cart"() {
        given:
        UUID itemId1 = UUID.randomUUID()
        UUID itemId2 = UUID.randomUUID()
        UUID listId = UUID.randomUUID()
        def itemIds = [itemId1, itemId2]
        CompletedToPendingItemStateService.RetryState processingState = new CompletedToPendingItemStateService.RetryState(false, false)
        UUID completedListId = UUID.randomUUID()
        def completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, null)

        when:
        def actual = completedToPendingItemStateService.processCompletedToPendingItemState(guestId, locationId, listId, itemIds, processingState).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse) // get completed cart
        1 * cartManager.getCompletedListCartContents(completedListId, true) >> Mono.error(new RuntimeException()) // get items in completed cart

        !actual.createItemsInPendingList
        !actual.deleteItemsInCompletedList
    }

    def "test processPendingItemEvent() with no item present in completed list"() {
        given:
        UUID itemId1 = UUID.randomUUID()
        UUID itemId2 = UUID.randomUUID()
        UUID listId = UUID.randomUUID()
        UUID completedListId = UUID.randomUUID()
        def itemIds = [itemId1, itemId2]
        CompletedToPendingItemStateService.RetryState processingState = new CompletedToPendingItemStateService.RetryState(false, false)
        def completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, null)

        when:
        def actual = completedToPendingItemStateService.processCompletedToPendingItemState(guestId, locationId, listId, itemIds, processingState).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse) // get completed cart
        1 * cartManager.getCompletedListCartContents(completedListId, true) >> Mono.empty() // get items in completed cart

        !actual.createItemsInPendingList
        !actual.deleteItemsInCompletedList
    }

    def "test processPendingItemEvent() with error from dedup"() {
        given:
        UUID itemId1 = UUID.randomUUID()
        UUID itemId2 = UUID.randomUUID()
        UUID listId = UUID.randomUUID()
        UUID completedListId = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "5678"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)
        def itemIds = [itemId1, itemId2]
        CompletedToPendingItemStateService.RetryState processingState = new CompletedToPendingItemStateService.RetryState(false, false)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, null)
        def completedItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, itemId1 , tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def completedCartItemResponse2 = cartDataProvider.getCartItemResponse(completedListId, itemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 3, "notes2", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def completedCartContent = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1, completedCartItemResponse2])

        def pendingItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID() , tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 3, "notes3", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartContent = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1])

        when:
        def actual = completedToPendingItemStateService.processCompletedToPendingItemState(guestId, locationId, listId, itemIds, processingState).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse) // get completed cart
        1 * cartManager.getCompletedListCartContents(completedListId, true) >> Mono.just(completedCartContent) // get items in completed cart
        1 * cartManager.getListCartContents(listId,true) >> Mono.just(pendingCartContent) //  dedup call
        1 * cartManager.updateMultiCartItems(_ as UpdateMultiCartItemsRequest) >> Mono.error(new RuntimeException())

        !actual.createItemsInPendingList
        !actual.deleteItemsInCompletedList
    }

    def "test processPendingItemEvent() with error from creating item in pending list"() {
        given:
        UUID itemId1 = UUID.randomUUID()
        UUID itemId2 = UUID.randomUUID()
        UUID listId = UUID.randomUUID()
        UUID completedListId = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "5678"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)
        def itemIds = [itemId1, itemId2]
        CompletedToPendingItemStateService.RetryState processingState = new CompletedToPendingItemStateService.RetryState(false, false)
        def recordMetadata = GroovyMock(RecordMetadata)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, null)
        def completedItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, itemId1 , tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def completedCartItemResponse2 = cartDataProvider.getCartItemResponse(completedListId, itemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 3, "notes2", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def completedCartContent = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1, completedCartItemResponse2])

        def pendingItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID() , tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 3, "notes3", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartContent = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1])

        def dedupedPendingCartItem =  cartDataProvider.getCartItemResponse(listId, pendingCartItemResponse1.cartItemId , tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 6, "notes3\nnotes2", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def updateMultiCartItemsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [dedupedPendingCartItem])

        when:
        def actual = completedToPendingItemStateService.processCompletedToPendingItemState(guestId, locationId, listId, itemIds, processingState).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse) // get completed cart
        1 * cartManager.getCompletedListCartContents(completedListId, true) >> Mono.just(completedCartContent) // get items in completed cart
        1 * cartManager.getListCartContents(listId,true) >> Mono.just(pendingCartContent) //  dedup call
        1 * cartManager.updateMultiCartItems(_ as UpdateMultiCartItemsRequest) >> { arguments ->
            final UpdateMultiCartItemsRequest updateMultiCartItemsRequest = arguments[0]
            assert updateMultiCartItemsRequest.cartId == listId
            assert updateMultiCartItemsRequest.cartItems.size() == 1
            Mono.just(updateMultiCartItemsResponse)
        } // update deduped items
        1 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata) // update item in completed list notify event
        1 * cartManager.addMultiCartItems(_ as AddMultiCartItemsRequest, _)  >> Mono.error(new RuntimeException())

        !actual.createItemsInPendingList
        !actual.deleteItemsInCompletedList
    }

    def "test processPendingItemEvent() with error deleting item in completed list"() {
        given:
        UUID itemId1 = UUID.randomUUID()
        UUID itemId2 = UUID.randomUUID()
        UUID listId = UUID.randomUUID()
        UUID completedListId = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "5678"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)
        def itemIds = [itemId1, itemId2]
        CompletedToPendingItemStateService.RetryState processingState = new CompletedToPendingItemStateService.RetryState(false, false)
        def recordMetadata = GroovyMock(RecordMetadata)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, null)
        def completedItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, itemId1 , tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def completedCartItemResponse2 = cartDataProvider.getCartItemResponse(completedListId, itemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 3, "notes2", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def completedCartContent = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1, completedCartItemResponse2])

        def pendingItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID() , tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 3, "notes3", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartContent = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1])

        def dedupedPendingCartItem =  cartDataProvider.getCartItemResponse(listId, pendingCartItemResponse1.cartItemId , tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 6, "notes3\nnotes2", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def updateMultiCartItemsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [dedupedPendingCartItem])

        def addedPendingCartItem = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID() , tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)

        when:
        def actual = completedToPendingItemStateService.processCompletedToPendingItemState(guestId, locationId, listId, itemIds, processingState).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse) // get completed cart
        1 * cartManager.getCompletedListCartContents(completedListId, true) >> Mono.just(completedCartContent) // get items in completed cart
        1 * cartManager.getListCartContents(listId,true) >> Mono.just(pendingCartContent) //  dedup call
        1 * cartManager.updateMultiCartItems(_ as UpdateMultiCartItemsRequest) >> { arguments ->
            final UpdateMultiCartItemsRequest updateMultiCartItemsRequest = arguments[0]
            assert updateMultiCartItemsRequest.cartId == listId
            assert updateMultiCartItemsRequest.cartItems.size() == 1
            Mono.just(updateMultiCartItemsResponse)
        } // update deduped items
        1 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata) // update item in completed list notify event
        1 * cartManager.addMultiCartItems(_ as AddMultiCartItemsRequest, _)  >> { arguments ->  // add new items
            final AddMultiCartItemsRequest addMultiCartItemsRequest = arguments[0]
            assert addMultiCartItemsRequest.cartId == listId
            assert addMultiCartItemsRequest.cartItems.size() == 1
            assert addMultiCartItemsRequest.cartItems.first().cartId == listId
            Mono.just([addedPendingCartItem])
        }
        1 * eventPublisher.publishEvent(CreateListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata) // create item in completed list notify event
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest) >> Mono.error(new RuntimeException())

        actual.createItemsInPendingList
        !actual.deleteItemsInCompletedList
    }


    def "test processPendingItemEvent() with no item found to be deleted from completed list"() {
        given:
        UUID itemId1 = UUID.randomUUID()
        UUID itemId2 = UUID.randomUUID()
        UUID listId = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "5678"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)
        UUID completedListId = UUID.randomUUID()
        def itemIds = [itemId1, itemId2]
        CompletedToPendingItemStateService.RetryState processingState = new CompletedToPendingItemStateService.RetryState(false, false)
        def recordMetadata = GroovyMock(RecordMetadata)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, null)
        def completedItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, itemId1 , tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def completedCartItemResponse2 = cartDataProvider.getCartItemResponse(completedListId, itemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 3, "notes2", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def completedCartContent = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1, completedCartItemResponse2])

        def pendingItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID() , tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 3, "notes3", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartContent = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1])

        def dedupedPendingCartItem =  cartDataProvider.getCartItemResponse(listId, pendingCartItemResponse1.cartItemId , tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 6, "notes3\nnotes2", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def updateMultiCartItemsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [dedupedPendingCartItem])

        def addedPendingCartItem = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID() , tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)

        when:
        def actual = completedToPendingItemStateService.processCompletedToPendingItemState(guestId, locationId, listId, itemIds, processingState).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse) // get completed cart
        1 * cartManager.getCompletedListCartContents(completedListId, true) >> Mono.just(completedCartContent) // get items in completed cart
        1 * cartManager.getListCartContents(listId,true) >> Mono.just(pendingCartContent) //  dedup call
        1 * cartManager.updateMultiCartItems(_ as UpdateMultiCartItemsRequest) >> { arguments ->
            final UpdateMultiCartItemsRequest updateMultiCartItemsRequest = arguments[0]
            assert updateMultiCartItemsRequest.cartId == listId
            assert updateMultiCartItemsRequest.cartItems.size() == 1
            Mono.just(updateMultiCartItemsResponse)
        } // update deduped items
        1 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata) // update item in completed list notify event
        1 * cartManager.addMultiCartItems(_ as AddMultiCartItemsRequest, _)  >> { arguments ->  // add new items
            final AddMultiCartItemsRequest addMultiCartItemsRequest = arguments[0]
            assert addMultiCartItemsRequest.cartId == listId
            assert addMultiCartItemsRequest.cartItems.size() == 1
            assert addMultiCartItemsRequest.cartItems.first().cartId == listId
            Mono.just([addedPendingCartItem])
        }
        1 * eventPublisher.publishEvent(CreateListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata) // create item in completed list notify event
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest) >> Mono.empty()

        actual.createItemsInPendingList
        actual.deleteItemsInCompletedList
    }


    def "test processPendingItemEvent() with partial state"() {
        given:
        UUID itemId1 = UUID.randomUUID()
        UUID itemId2 = UUID.randomUUID()
        UUID listId = UUID.randomUUID()
        UUID completedListId = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "5678"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)
        def itemIds = [itemId1, itemId2]
        CompletedToPendingItemStateService.RetryState processingState = new CompletedToPendingItemStateService.RetryState(true, false)
        def recordMetadata = GroovyMock(RecordMetadata)
        def completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, null)
        def completedItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, itemId1, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def completedCartItemResponse2 = cartDataProvider.getCartItemResponse(completedListId, itemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 3, "notes2", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def completedCartContent = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1, completedCartItemResponse2])

        def completedCartItemsDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(completedListId,
                [completedCartItemResponse1.cartItemId, completedCartItemResponse2.cartItemId], null)

        when:
        def actual = completedToPendingItemStateService.processCompletedToPendingItemState(guestId, locationId, listId, itemIds, processingState).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse) // get completed cart
        1 * cartManager.getCompletedListCartContents(completedListId, true) >> Mono.just(completedCartContent) // get items in completed cart
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest) >> { arguments -> // delete items in completed list
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartId == completedListId
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0] == completedCartItemResponse1.cartItemId
            assert request.cartItemIds[1] == completedCartItemResponse2.cartItemId
            Mono.just(completedCartItemsDeleteResponse)
        }
        1 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _, listId.toString()) >> Mono.just(recordMetadata) // delete item in pending list notify event

        actual.createItemsInPendingList
        actual.deleteItemsInCompletedList
    }

    def "test processPendingItemEvent() with completed processing state"() {
        given:
        UUID itemId1 = UUID.randomUUID()
        UUID itemId2 = UUID.randomUUID()
        UUID listId = UUID.randomUUID()
        def itemIds = [itemId1, itemId2]
        CompletedToPendingItemStateService.RetryState processingState = new CompletedToPendingItemStateService.RetryState(true, true)

        when:
        def actual = completedToPendingItemStateService.processCompletedToPendingItemState(guestId, locationId, listId, itemIds, processingState).block()

        then:

        actual.createItemsInPendingList
        actual.deleteItemsInCompletedList
    }


    def "test processPendingItemEvent() with unknown processing state"() {
        given:
        UUID itemId1 = UUID.randomUUID()
        UUID itemId2 = UUID.randomUUID()
        UUID listId = UUID.randomUUID()
        def itemIds = [itemId1, itemId2]
        CompletedToPendingItemStateService.RetryState processingState = new CompletedToPendingItemStateService.RetryState(false, true)

        when:
        def actual = completedToPendingItemStateService.processCompletedToPendingItemState(guestId, locationId, listId, itemIds, processingState).block()

        then:

        actual.createItemsInPendingList
        actual.deleteItemsInCompletedList
    }
}
