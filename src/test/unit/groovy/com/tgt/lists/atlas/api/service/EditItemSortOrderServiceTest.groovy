package com.tgt.lists.atlas.api.service


import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.common.components.exception.BadRequestException
import com.tgt.lists.atlas.api.domain.CartManager
import com.tgt.lists.atlas.api.domain.EventPublisher
import com.tgt.lists.atlas.api.transport.EditItemSortOrderRequestTO
import com.tgt.lists.atlas.api.transport.ListItemMetaDataTO
import com.tgt.lists.atlas.api.transport.UserItemMetaDataTO
import com.tgt.lists.atlas.api.util.Direction
import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.util.CartDataProvider
import com.tgt.lists.atlas.util.TestListChannel
import com.tgt.lists.atlas.util.TestUtilConstants
import org.apache.kafka.clients.producer.RecordMetadata
import reactor.core.publisher.Mono
import spock.lang.Specification

class EditItemSortOrderServiceTest extends Specification {

    CartManager cartManager
    EventPublisher eventPublisher
    CartDataProvider cartDataProvider
    EditItemSortOrderService editItemSortOrderService

    def setup() {
        cartDataProvider = new CartDataProvider()
        cartDataProvider = new CartDataProvider()
        cartManager = Mock(CartManager)
        eventPublisher = Mock(EventPublisher)
        editItemSortOrderService = Mock(EditItemSortOrderService)
        editItemSortOrderService = new EditItemSortOrderService(eventPublisher, cartManager)
    }

    def "Test editItemPosition() when primary and secondary item id are same"() {
        given:
        UUID listId = UUID.randomUUID()
        UUID primaryItemId = UUID.randomUUID()
        UUID secondaryItemId = primaryItemId
        Direction direction = Direction.ABOVE
        EditItemSortOrderRequestTO editItemSortOrderRequestTO = new EditItemSortOrderRequestTO(listId, primaryItemId, secondaryItemId, direction)

        def pendingCartResponse = cartDataProvider.getCartResponse(listId, "1234",
                TestListChannel.MOBILE.toString(), CartType.LIST, "Pending list", "My pending list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, primaryItemId, "1234", TestListChannel.WEB.toString(), "1234",
                "title1", 3, "note\nnote",10, 10, "Stand Alone",
                "READY", "some-url", "some-image",
                cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.GENERIC_ITEM, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, primaryItemId, "1234", TestListChannel.WEB.toString(), "1234",
                "itemTitle", 1, "itemNote",10, 10, "Stand Alone",
                "READY", "some-url", "some-image",
                cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)

        def cartContentsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])

        when:
        def actual = editItemSortOrderService.editItemPosition(editItemSortOrderRequestTO).block()

        then:
        1 * cartManager.getListCartContents(_, true) >> Mono.just(cartContentsResponse)
        
        actual
    }

    def "Test editItemPosition() when primary and secondary item id are different"() {
        given:
        UUID listId = UUID.randomUUID()
        UUID primaryItemId = UUID.randomUUID()
        UUID secondaryItemId = UUID.randomUUID()
        Direction direction = Direction.ABOVE
        EditItemSortOrderRequestTO editItemSortOrderRequestTO = new EditItemSortOrderRequestTO(listId, primaryItemId, secondaryItemId, direction)

        def pendingCartResponse = cartDataProvider.getCartResponse(listId, "1234",
                TestListChannel.MOBILE.toString(), CartType.LIST, "Pending list", "My pending list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, primaryItemId, "1234", TestListChannel.WEB.toString(), "1234",
                "title1", 3, "note\nnote",10, 10, "Stand Alone",
                "READY", "some-url", "some-image",
                cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.GENERIC_ITEM, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, secondaryItemId, "1234", TestListChannel.WEB.toString(), "1234",
                "itemTitle", 1, "itemNote",10, 10, "Stand Alone",
                "READY", "some-url", "some-image",
                cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)

        def cartContentsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])

        when:
        def actual = editItemSortOrderService.editItemPosition(editItemSortOrderRequestTO).block()

        then:
        1 * eventPublisher.publishEvent(_,_,_) >> Mono.just(GroovyMock(RecordMetadata))
        1 * cartManager.getListCartContents(_, true) >> Mono.just(cartContentsResponse)

        actual
    }

    def "test editListPosition() when unauthorized items ids are passed"() {
        given:
        UUID listId = UUID.randomUUID()
        UUID primaryItemId = UUID.randomUUID()
        UUID secondaryItemId = UUID.randomUUID()
        Direction direction = Direction.ABOVE

        EditItemSortOrderRequestTO editItemSortOrderRequestTO = new EditItemSortOrderRequestTO(listId, primaryItemId, secondaryItemId, direction)

        def pendingCartResponse = cartDataProvider.getCartResponse(listId, "1234",
                TestListChannel.MOBILE.toString(), CartType.LIST, "Pending list", "My pending list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), "1234", TestListChannel.WEB.toString(), "1234",
                "title1", 3, "note\nnote",10, 10, "Stand Alone",
                "READY", "some-url", "some-image",
                cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.GENERIC_ITEM, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), "1234", TestListChannel.WEB.toString(), "1234",
                "itemTitle", 1, "itemNote",10, 10, "Stand Alone",
                "READY", "some-url", "some-image",
                cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)

        def cartContentsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])

        when:
        editItemSortOrderService.editItemPosition(editItemSortOrderRequestTO).block()

        then:
        1 * cartManager.getListCartContents(_, true) >> Mono.just(cartContentsResponse)

        thrown BadRequestException

    }
}
