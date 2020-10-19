package com.tgt.lists.atlas.api.service


import com.tgt.lists.cart.transport.DeleteCartItemRequest
import com.tgt.lists.atlas.api.domain.CartManager
import com.tgt.lists.atlas.api.domain.DeleteCartItemsManager
import com.tgt.lists.atlas.api.domain.EventPublisher
import com.tgt.lists.atlas.api.transport.ListItemMetaDataTO
import com.tgt.lists.atlas.api.transport.UserItemMetaDataTO
import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.kafka.model.DeleteListItemNotifyEvent
import com.tgt.lists.atlas.util.CartDataProvider
import com.tgt.lists.atlas.util.TestListChannel
import org.apache.kafka.clients.producer.RecordMetadata
import reactor.core.publisher.Mono
import spock.lang.Specification

class DeleteListItemServiceTest extends Specification {
    
    CartManager cartManager
    EventPublisher eventPublisher
    DeleteListItemService deleteListItemService
    DeleteCartItemsManager deleteCartItemsManager
    CartDataProvider cartDataProvider
    String guestId = "1234"

    def setup() {
        cartManager = Mock(CartManager)
        eventPublisher = Mock(EventPublisher)
        deleteCartItemsManager = new DeleteCartItemsManager(cartManager, eventPublisher, true)
        deleteListItemService = new DeleteListItemService(deleteCartItemsManager)
        cartDataProvider = new CartDataProvider()
    }

    def "test deleteListItem() in pending cart"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId = UUID.randomUUID()
        def cartItemDeleteResponse = cartDataProvider.getCartItemDeleteResponse(listId, listItemId)
        def recordMetadata = GroovyMock(RecordMetadata)
        def tcin1 = "1234"
        def tenantRefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse = cartDataProvider.getCartItemResponse(listId, listItemId, tenantRefId1, TestListChannel.WEB.toString(), tcin1,
            "some title", 1, "some note", 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        when:
        def actual = deleteListItemService.deleteListItem(guestId, listId, listItemId).block()

        then:
        1 * cartManager.getCartItem(listId, listItemId) >> Mono.just(pendingCartItemResponse)
        1 * cartManager.deleteCartItem(_ as DeleteCartItemRequest) >> { arguments ->
            final DeleteCartItemRequest request = arguments[0]
            assert request.cartId == listId
            assert request.cartItemId == listItemId
            Mono.just(cartItemDeleteResponse)
        }
        1 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata)

        actual.listId == cartItemDeleteResponse.cartId
        actual.listItemId == cartItemDeleteResponse.cartItemId
    }

    def "test deleteListItem() in pending cart with exception"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantRefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse = cartDataProvider.getCartItemResponse(listId, listItemId, tenantRefId1, TestListChannel.WEB.toString(), tcin1,
                "some title", 1, "some note", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        when:
        deleteListItemService.deleteListItem(guestId, listId, listItemId).block()

        then:
        1 * cartManager.getCartItem(listId, listItemId) >> Mono.just(pendingCartItemResponse)
        1 * cartManager.deleteCartItem(_ as DeleteCartItemRequest) >> { arguments ->
            final DeleteCartItemRequest request = arguments[0]
            assert request.cartId == listId
            assert request.cartItemId == listItemId
            Mono.error(new RuntimeException("some exception"))
        }

        thrown(RuntimeException)
    }

    def "test deleteListItem() in completed cart"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantRefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)

        def cartItemDeleteResponse = cartDataProvider.getCartItemDeleteResponse(completedListId, listItemId)

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse = cartDataProvider.getCartItemResponse(completedListId, listItemId, tenantRefId1, TestListChannel.WEB.toString(), tcin1,
                "some title", 1, "some note", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)
        def recordMetadata = GroovyMock(RecordMetadata)

        when:
        def actual = deleteListItemService.deleteListItem(guestId, listId, listItemId).block()

        then:
        1 * cartManager.getCartItem(listId, listItemId) >> Mono.empty()
        1 * cartManager.getItemInCompletedCart(listId, listItemId) >> Mono.just(completedCartItemResponse)
        1 * cartManager.deleteCartItem(_ as DeleteCartItemRequest) >> { arguments ->
            final DeleteCartItemRequest request = arguments[0]
            assert request.cartId == completedListId
            assert request.cartItemId == listItemId
            Mono.just(cartItemDeleteResponse)
        }
        1 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata)

        actual.listId == listId
        actual.listItemId == cartItemDeleteResponse.cartItemId
    }

    def "test deleteListItem() in completed cart with exception from completed cart"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantRefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse = cartDataProvider.getCartItemResponse(completedListId, listItemId, tenantRefId1, TestListChannel.WEB.toString(), tcin1,
                "some title", 1, "some note", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        when:
        deleteListItemService.deleteListItem(guestId, listId, listItemId).block()

        then:
        1 * cartManager.getCartItem(listId, listItemId) >> Mono.empty()
        1 * cartManager.getItemInCompletedCart(listId, listItemId) >> Mono.just(completedCartItemResponse)
        1 * cartManager.deleteCartItem(_ as DeleteCartItemRequest) >> { arguments ->
            final DeleteCartItemRequest request = arguments[0]
            assert request.cartId == completedListId
            assert request.cartItemId == listItemId
            Mono.error(new RuntimeException("some exception"))
        }

        thrown(RuntimeException)
    }

    def "test deleteListItem() in completed cart with exception"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId = UUID.randomUUID()

        when:
        deleteListItemService.deleteListItem(guestId, listId, listItemId).block()

        then:
        1 * cartManager.getItemInCompletedCart(listId, listItemId) >> Mono.error(new RuntimeException("some exception"))
        1 * cartManager.getCartItem(listId, listItemId) >> Mono.empty()

        thrown(RuntimeException)
    }

    def "test deleteListItem() in completed cart with no cart item to delete"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId = UUID.randomUUID()

        when:
        def actual = deleteListItemService.deleteListItem(guestId, listId, listItemId).block()

        then:
        1 * cartManager.getCartItem(listId, listItemId) >> Mono.empty()
        1 * cartManager.getItemInCompletedCart(listId, listItemId) >> Mono.empty()

        actual == null
    }
}
