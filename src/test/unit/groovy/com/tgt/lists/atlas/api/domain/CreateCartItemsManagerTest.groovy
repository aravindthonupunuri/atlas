package com.tgt.lists.atlas.api.domain


import com.tgt.lists.cart.transport.CartItemsRequest
import com.tgt.lists.atlas.api.transport.ListItemMetaDataTO
import com.tgt.lists.atlas.api.transport.ListItemRequestTO
import com.tgt.lists.atlas.api.transport.UserItemMetaDataTO
import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.api.util.UnitOfMeasure
import com.tgt.lists.atlas.kafka.model.CreateListItemNotifyEvent
import com.tgt.lists.atlas.util.CartDataProvider
import com.tgt.lists.atlas.util.TestListChannel
import org.apache.kafka.clients.producer.RecordMetadata
import reactor.core.publisher.Mono
import spock.lang.Specification

class CreateCartItemsManagerTest extends Specification {

    CartManager cartManager
    EventPublisher eventPublisher
    CreateCartItemsManager createCartItemsManager
    CartDataProvider cartDataProvider = new CartDataProvider()
    String guestId = "1234"

    def setup() {
        cartManager = Mock(CartManager)
        eventPublisher = Mock(EventPublisher)
        createCartItemsManager = new CreateCartItemsManager(cartManager, eventPublisher)
    }

    def "Test createListItem() happy path"() {
        given:
        def listId = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def listItemRequest = new ListItemRequestTO(ItemType.TCIN, tenantrefId1, TestListChannel.WEB.toString(), tcin1, null,
            "test item", null, UnitOfMeasure.EACHES, null)

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            listItemRequest.itemTitle, 1, listItemRequest.itemNote, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        when:
        def actual = createCartItemsManager.createListItem(guestId, listId, 1357L, listItemRequest, null).block()

        then:
        actual == cartItemResponse

        1 * cartManager.createCartItem(_) >> { arguments ->
            final CartItemsRequest request = arguments[0]
            assert request.tenantReferenceId == listItemRequest.itemRefId
            Mono.just(cartItemResponse)
        }
        1 * eventPublisher.publishEvent(CreateListItemNotifyEvent.eventType, _, listId.toString()) >> Mono.just(GroovyMock(RecordMetadata))
    }

    def "Test createListItem() with Completed item state"() {
        given:
        def listId = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def listItemRequest = new ListItemRequestTO(ItemType.TCIN, tenantrefId1, TestListChannel.WEB.toString(), tcin1, null,
            "test item", null, UnitOfMeasure.EACHES, null)

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            listItemRequest.itemTitle, 1, listItemRequest.itemNote, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        when:
        def actual = createCartItemsManager.createListItem(guestId, listId, 1357L, listItemRequest, LIST_ITEM_STATE.COMPLETED).block()

        then:
        actual == cartItemResponse

        1 * cartManager.createCartItem(_) >> { arguments ->
            final CartItemsRequest request = arguments[0]
            assert request.tenantReferenceId == listItemRequest.itemRefId
            def listItemMetaData = cartDataProvider.getListItemMetaDataFromCart(request.metadata as Map)
            assert listItemMetaData.getItemState() == LIST_ITEM_STATE.COMPLETED
            Mono.just(cartItemResponse)
        }
        1 * eventPublisher.publishEvent(CreateListItemNotifyEvent.eventType, _, listId.toString()) >> Mono.just(GroovyMock(RecordMetadata))
    }
}
