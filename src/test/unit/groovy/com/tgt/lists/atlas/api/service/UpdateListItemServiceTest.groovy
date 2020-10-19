package com.tgt.lists.atlas.api.service


import com.tgt.lists.cart.transport.CartItemUpdateRequest
import com.tgt.lists.common.components.exception.BadRequestException
import com.tgt.lists.atlas.api.domain.*
import com.tgt.lists.atlas.api.service.transform.list_items.UserItemMetaDataTransformationStep
import com.tgt.lists.atlas.api.transport.*
import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.api.util.LIST_STATUS
import com.tgt.lists.atlas.kafka.model.CompletionItemActionEvent
import com.tgt.lists.atlas.kafka.model.PendingItemActionEvent
import com.tgt.lists.atlas.kafka.model.UpdateListItemNotifyEvent
import com.tgt.lists.atlas.util.CartDataProvider
import com.tgt.lists.atlas.util.TestListChannel
import org.apache.kafka.clients.producer.RecordMetadata
import org.jetbrains.annotations.NotNull
import reactor.core.publisher.Mono
import spock.lang.Specification

class UpdateListItemServiceTest extends Specification {

    CartManager cartManager
    EventPublisher eventPublisher
    DeduplicationManager deduplicationManager
    AddMultiItemsManager addMultiItemsManager
    UpdateListItemService updateListItemService
    DeleteCartItemsManager deleteCartItemsManager
    CreateCartItemsManager createCartItemsManager
    UpdateCartItemsManager updateCartItemsManager
    CartDataProvider cartDataProvider
    String guestId = "1234"
    Long locationId = 1375L

    def setup() {
        cartManager = Mock(CartManager)
        eventPublisher = Mock(EventPublisher)
        deleteCartItemsManager = new DeleteCartItemsManager(cartManager, eventPublisher, true)
        createCartItemsManager = new CreateCartItemsManager(cartManager, eventPublisher)
        updateCartItemsManager = new UpdateCartItemsManager(cartManager, eventPublisher)
        deduplicationManager = new DeduplicationManager(cartManager, updateCartItemsManager, deleteCartItemsManager, true, 10, 10, false)
        addMultiItemsManager = new AddMultiItemsManager(deduplicationManager, createCartItemsManager)
        updateListItemService = new UpdateListItemService(cartManager, eventPublisher, updateCartItemsManager, true)
        cartDataProvider = new CartDataProvider()

    }

    def "test updateListItem() integrity"() {
        given:
        def listItemUpdateRequest = new ListItemUpdateRequestTO(null, "updated item note", null,
            null,3, null)
        def listId = UUID.randomUUID()
        def itemId = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)

        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, itemId, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            "title", 1, "note",  10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()), null, null, null)

        def updatedCartItemResponse = cartDataProvider.getCartItemResponse(listId, itemId, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            listItemUpdateRequest.itemTitle, listItemUpdateRequest.requestedQuantity, listItemUpdateRequest.itemNote,
                10, 10, "Stand Alone", "READY", "some-url",
                "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()), null, null, null)

        def metaData = cartDataProvider.getListItemMetaDataFromCart(updatedCartItemResponse.metadata)

        def recordMetadata = GroovyMock(RecordMetadata)

        when:
        def actual = updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()

        then:
        1 * cartManager.getCartItem(listId, itemId) >> Mono.just(cartItemResponse)
        1 * cartManager.updateCartItem(itemId, _) >> { arguments ->  // call to create item in pending list
            final CartItemUpdateRequest request = arguments[1]
            assert request.cartId == listId
            def listItemMetaData = cartDataProvider.getListItemMetaDataFromCart(request.metadata as Map)
            assert listItemMetaData.getItemState() == LIST_ITEM_STATE.PENDING
            Mono.just(updatedCartItemResponse)
        }
        1 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _, _) >>  Mono.just(recordMetadata)

        actual.listItemId == updatedCartItemResponse.cartItemId
        actual.tcin == updatedCartItemResponse.tcin
        actual.itemTitle == updatedCartItemResponse.shortDescription
        actual.itemNote == updatedCartItemResponse.notes
        actual.price == updatedCartItemResponse.price
        actual.listPrice == updatedCartItemResponse.listPrice
        actual.images == updatedCartItemResponse.images
        actual.itemType == metaData.itemType
    }

    def "test updateListItem() integrity with userItemMetaDataTransformationStep"() {
        given:

        def registryItemMetaData = [
                "registry-metadata": [
                        "sub_channel" : "KIOSK"
                ]
        ]
        def userItemMetaData = [
                "user_meta_data" : [
                        registryItemMetaData
                ]
        ]

        def userItemMetaDataTransformationStep = new DefaultUserItemMetaDataTransformation()

        def listItemUpdateRequest = new ListItemUpdateRequestTO(null, "updated item note", userItemMetaData,
                null,3, userItemMetaDataTransformationStep)
        def listId = UUID.randomUUID()
        def itemId = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)

        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, itemId, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 1, "note",  10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO(registryItemMetaData)), null, null, null)

        def updatedCartItemResponse = cartDataProvider.getCartItemResponse(listId, itemId, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                listItemUpdateRequest.itemTitle, listItemUpdateRequest.requestedQuantity, listItemUpdateRequest.itemNote,
                10, 10, "Stand Alone", "READY", "some-url",
                "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO(registryItemMetaData)), null, null, null)

        def metaData = cartDataProvider.getListItemMetaDataFromCart(updatedCartItemResponse.metadata)

        def recordMetadata = GroovyMock(RecordMetadata)

        when:
        def actual = updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()

        then:
        1 * cartManager.getCartItem(_ ,_) >> Mono.just(cartItemResponse)
        1 * cartManager.updateCartItem(itemId, _) >> { arguments ->  // call to create item in pending list
            final CartItemUpdateRequest request = arguments[1]
            assert request.cartId == listId
            def listItemMetaData = cartDataProvider.getListItemMetaDataFromCart(request.metadata as Map)
            assert listItemMetaData.getItemState() == LIST_ITEM_STATE.PENDING
            Mono.just(updatedCartItemResponse)
        }
        1 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _, _) >>  Mono.just(recordMetadata)

        actual.listItemId == updatedCartItemResponse.cartItemId
        actual.tcin == updatedCartItemResponse.tcin
        actual.itemTitle == updatedCartItemResponse.shortDescription
        actual.itemNote == updatedCartItemResponse.notes
        actual.price == updatedCartItemResponse.price
        actual.listPrice == updatedCartItemResponse.listPrice
        actual.images == updatedCartItemResponse.images
        actual.itemType == metaData.itemType
    }

    def "test updateListItem() integrity with empty item title"() {
        given:
        def listItemUpdateRequest = new ListItemUpdateRequestTO("  ", "updated item note", null, null,3, null)
        def listId = UUID.randomUUID()
        def itemId = UUID.randomUUID()

        when:
        updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()

        then:
        thrown(BadRequestException)
    }

    def "test updateListItem() integrity with empty item title and item state change"() {
        given:
        def listItemUpdateRequest = new ListItemUpdateRequestTO("  ", "updated item note", null, LIST_ITEM_STATE.PENDING,3, null)
        def listId = UUID.randomUUID()
        def itemId = UUID.randomUUID()

        when:
        updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()

        then:
        thrown(BadRequestException)
    }

    def "test updateListItem() for tcin_item passing item title"() {
        given:
        def listItemUpdateRequest = new ListItemUpdateRequestTO("item_title", "updated item note", null, null,3, null)
        def listId = UUID.randomUUID()
        def itemId = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)

        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, itemId, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 1, "note",  10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()), null, null, null)
        when:
        updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()

        then:
        thrown(BadRequestException)

        1 * cartManager.getCartItem(_ ,_) >> Mono.just(cartItemResponse)
    }

    def "test updateListItem() with updating only the item state from pending to completed"() {
        given:
        def listItemUpdateRequest = new ListItemUpdateRequestTO(null, null, null, LIST_ITEM_STATE.COMPLETED,null, null)
        def listId = UUID.randomUUID()
        def itemId = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, itemId, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 1, "note",  10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()), null, null, null)
        def recordMetadata = GroovyMock(RecordMetadata)

        when:
        def actual = updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()

        then:
        1 * cartManager.getCartItem(_ ,_ ) >> Mono.just(cartItemResponse)
        1 * eventPublisher.publishEvent(CompletionItemActionEvent.getEventType(), _, listId.toString()) >>  Mono.just(recordMetadata)

        actual.listItemId == cartItemResponse.cartItemId
        actual.tcin == cartItemResponse.tcin
        actual.itemTitle == cartItemResponse.shortDescription
        actual.itemNote == cartItemResponse.notes
        actual.price == cartItemResponse.price
        actual.listPrice == cartItemResponse.listPrice
        actual.images == cartItemResponse.images
        actual.itemState == listItemUpdateRequest.itemState
    }

    def "test updateListItem() with updating only the item state change and error getting cart item"() {
        given:
        def listItemUpdateRequest = new ListItemUpdateRequestTO(null, null, null, LIST_ITEM_STATE.COMPLETED,null, null)
        def listId = UUID.randomUUID()
        def itemId = UUID.randomUUID()

        when:
        updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()

        then:
        1 * cartManager.getCartItem(_ ,_) >> Mono.error(new RuntimeException())

        thrown(RuntimeException)
    }

    def "test updateListItem() with updating item state from pending to completed"() {
        given:
        def listItemUpdateRequest = new ListItemUpdateRequestTO(null, "updated item note", null, LIST_ITEM_STATE.COMPLETED,1, null)
        def listId = UUID.randomUUID()
        def itemId = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)

        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, itemId, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            "title", 1, "note",  10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()), null, null, null)

        ListItemMetaDataTO updatedItemMetaData = new ListItemMetaDataTO(ItemType.TCIN,
            LIST_ITEM_STATE.PENDING)
        def updatedCartItemResponse = cartDataProvider.getCartItemResponse(listId, itemId, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            listItemUpdateRequest.itemTitle, 1, listItemUpdateRequest.itemNote,  10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(updatedItemMetaData, new UserItemMetaDataTO()), null, null, null)

        def recordMetadata = GroovyMock(RecordMetadata)
        def metaData = cartDataProvider.getListItemMetaDataFromCart(updatedCartItemResponse.metadata)

        when:
        def actual = updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()

        then:
        1 * cartManager.getCartItem(_ ,_) >> Mono.just(cartItemResponse)
        1 * cartManager.updateCartItem(itemId, _) >> { arguments ->
            final CartItemUpdateRequest request = arguments[1]
            def listItemMetaData = cartDataProvider.getListItemMetaDataFromCart(request.metadata as Map)
            assert listItemMetaData.getItemState() == LIST_ITEM_STATE.PENDING // Item state not changed to completed, its done in the asyc part of the functionality
            Mono.just(updatedCartItemResponse)
        }
        1 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _, _) >>  Mono.just(recordMetadata)
        1 * eventPublisher.publishEvent(CompletionItemActionEvent.getEventType(), _, listId.toString()) >>  Mono.just(recordMetadata)

        actual.listItemId == updatedCartItemResponse.cartItemId
        actual.tcin == updatedCartItemResponse.tcin
        actual.itemTitle == updatedCartItemResponse.shortDescription
        actual.itemNote == updatedCartItemResponse.notes
        actual.price == updatedCartItemResponse.price
        actual.listPrice == updatedCartItemResponse.listPrice
        actual.images == updatedCartItemResponse.images
        actual.itemType == metaData.itemType
        actual.itemState == LIST_ITEM_STATE.COMPLETED
    }

    def "test updateListItem() with updating item state from completed to pending"() {
        given:
        def listItemUpdateRequest = new ListItemUpdateRequestTO(null, "updated item note", null, LIST_ITEM_STATE.PENDING,1, null)
        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def itemId = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)

        def completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, listId.toString(),
                cartDataProvider.getMetaData(new ListMetaDataTO(false,
                        LIST_STATUS.COMPLETED), new UserMetaDataTO()))

        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def cartItemResponse = cartDataProvider.getCartItemResponse(completedListId, itemId, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 1, "note",  10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()), null, null, null)

        ListItemMetaDataTO updatedItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def updatedCartItemResponse = cartDataProvider.getCartItemResponse(listId, itemId, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                listItemUpdateRequest.itemTitle, 1, listItemUpdateRequest.itemNote, 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(updatedItemMetaData, new UserItemMetaDataTO()), null, null, null)
        def recordMetadata = GroovyMock(RecordMetadata)
        def metaData = cartDataProvider.getListItemMetaDataFromCart(updatedCartItemResponse.metadata)

        when:
        def actual = updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()

        then:
        1 * cartManager.getCartItem(_, _) >> Mono.empty()
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse)
        1 * cartManager.getCartItem(completedListId, itemId) >> Mono.just(cartItemResponse)
        1 * cartManager.updateCartItem(itemId, _) >> { arguments ->
            final CartItemUpdateRequest request = arguments[1]
            def listItemMetaData = cartDataProvider.getListItemMetaDataFromCart(request.metadata as Map)
            assert listItemMetaData.getItemState() == LIST_ITEM_STATE.COMPLETED // Item state not changed to pending, its done in the async part of the functionality
            Mono.just(updatedCartItemResponse)
        }
        1 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _, _) >>  Mono.just(recordMetadata)
        1 * eventPublisher.publishEvent(PendingItemActionEvent.getEventType(), _, listId.toString()) >>  Mono.just(recordMetadata)

        actual.listItemId == updatedCartItemResponse.cartItemId
        actual.tcin == updatedCartItemResponse.tcin
        actual.itemTitle == updatedCartItemResponse.shortDescription
        actual.itemNote == updatedCartItemResponse.notes
        actual.price == updatedCartItemResponse.price
        actual.listPrice == updatedCartItemResponse.listPrice
        actual.images == updatedCartItemResponse.images
        actual.itemType == metaData.itemType
        actual.itemState == LIST_ITEM_STATE.PENDING

    }

    def "test updateListItem() with empty request object"() {
        given:
        def listItemUpdateRequest = new ListItemUpdateRequestTO("                   ", "", null, null,null, null)
        def listId = UUID.randomUUID()
        def itemId = UUID.randomUUID()

        when:
        updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()

        then:
        thrown(BadRequestException)
    }

    def "test updateListItem() with error getting item"() {
        given:
        def listItemUpdateRequest = new ListItemUpdateRequestTO("updated item title", "updated item note", null, LIST_ITEM_STATE.PENDING,1, null)
        def listId = UUID.randomUUID()
        def itemId = UUID.randomUUID()

        when:
        updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()

        then:
        1 * cartManager.getCartItem(_ ,_) >>  Mono.error(new RuntimeException())

        thrown(RuntimeException)
    }

    def "test updateListItem() when failure to publish completion item action event into message bus topic and DLQ topic"() {
        given:
        def listItemUpdateRequest = new ListItemUpdateRequestTO(null, "updated item note", null, LIST_ITEM_STATE.COMPLETED,1, null)
        def listId = UUID.randomUUID()
        def itemId = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)

        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, itemId, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 1, "note",  10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()), null, null, null)

        ListItemMetaDataTO updatedItemMetaData = new ListItemMetaDataTO(ItemType.TCIN,
                LIST_ITEM_STATE.PENDING)
        def updatedCartItemResponse = cartDataProvider.getCartItemResponse(listId, itemId, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                listItemUpdateRequest.itemTitle, 1, listItemUpdateRequest.itemNote,  10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(updatedItemMetaData, new UserItemMetaDataTO()), null, null, null)
        def recordMetadata = GroovyMock(RecordMetadata)
        def metaData = cartDataProvider.getListItemMetaDataFromCart(updatedCartItemResponse.metadata)

        when:
        def actual = updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()

        then:
        1 * cartManager.getCartItem(_ ,_) >> Mono.just(cartItemResponse)
        1 * cartManager.updateCartItem(itemId, _) >> { arguments ->
            final CartItemUpdateRequest request = arguments[1]
            def listItemMetaData = cartDataProvider.getListItemMetaDataFromCart(request.metadata as Map)
            assert listItemMetaData.getItemState() == LIST_ITEM_STATE.PENDING // Item state not changed to completed, its done in the asyc part of the functionality
            Mono.just(updatedCartItemResponse)
        }
        1 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _, _) >>  Mono.just(recordMetadata)
        1 * eventPublisher.publishEvent(CompletionItemActionEvent.getEventType(), _, listId.toString()) >>  Mono.error(new RuntimeException())

        actual.listItemId == updatedCartItemResponse.cartItemId
        actual.tcin == updatedCartItemResponse.tcin
        actual.itemTitle == updatedCartItemResponse.shortDescription
        actual.itemNote == updatedCartItemResponse.notes
        actual.price == updatedCartItemResponse.price
        actual.listPrice == updatedCartItemResponse.listPrice
        actual.images == updatedCartItemResponse.images
        actual.itemType == metaData.itemType
    }

    def "test onlyItemStateUpdate() integrity "() {
        given:
        def listItemUpdateRequest = new ListItemUpdateRequestTO(null, null, null, LIST_ITEM_STATE.COMPLETED,null, null)

        when:
        def actual = listItemUpdateRequest.onlyItemStateUpdate()

        then:
        actual
    }

    class DefaultUserItemMetaDataTransformation implements UserItemMetaDataTransformationStep {

        @Override
        Mono<UserItemMetaDataTO> execute(@NotNull UserItemMetaDataTO userItemMetaDataTO) {
            return Mono.just(userItemMetaDataTO)
        }
    }
}