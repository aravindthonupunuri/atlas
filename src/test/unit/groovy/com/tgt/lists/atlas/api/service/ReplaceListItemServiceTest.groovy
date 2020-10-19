package com.tgt.lists.atlas.api.service

import com.tgt.lists.cart.transport.AddMultiCartItemsRequest
import com.tgt.lists.atlas.api.domain.*
import com.tgt.lists.atlas.api.transport.ListItemMetaDataTO
import com.tgt.lists.atlas.api.transport.ListItemRequestTO
import com.tgt.lists.atlas.api.transport.UserItemMetaDataTO
import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.api.util.UnitOfMeasure
import com.tgt.lists.atlas.kafka.model.CreateListItemNotifyEvent
import com.tgt.lists.atlas.kafka.model.DeleteListItemNotifyEvent
import com.tgt.lists.atlas.util.CartDataProvider
import com.tgt.lists.atlas.util.TestListChannel
import org.apache.kafka.clients.producer.RecordMetadata
import reactor.core.publisher.Mono
import spock.lang.Specification

class ReplaceListItemServiceTest extends Specification {
    
    CartManager cartManager
    EventPublisher eventPublisher
    CartDataProvider cartDataProvider
    AddMultiItemsManager addMultiItemsManager
    DeduplicationManager deduplicationManager
    CreateListItemService createListItemService
    DeleteListItemService deleteListItemService
    DeleteCartItemsManager deleteCartItemsManager
    CreateCartItemsManager createCartItemsManager
    UpdateCartItemsManager updateCartItemsManager
    ReplaceListItemService replaceListItemService
    ContextContainerManager contextContainerManager
    String guestId = "1234"

    def setup() {
        cartManager = Mock(CartManager)
        eventPublisher = Mock(EventPublisher)
        updateCartItemsManager = new UpdateCartItemsManager(cartManager, eventPublisher)
        deleteCartItemsManager = new DeleteCartItemsManager( cartManager, eventPublisher, true)
        deduplicationManager = new DeduplicationManager(cartManager, updateCartItemsManager, deleteCartItemsManager, true, 10, 10, false)
        contextContainerManager = new ContextContainerManager()
        createCartItemsManager = new CreateCartItemsManager(cartManager, eventPublisher)
        addMultiItemsManager = new AddMultiItemsManager(deduplicationManager, createCartItemsManager)
        createListItemService = new CreateListItemService(addMultiItemsManager)
        cartDataProvider = new CartDataProvider()
        deleteCartItemsManager = new DeleteCartItemsManager(cartManager, eventPublisher, true)
        deleteListItemService = new DeleteListItemService(deleteCartItemsManager)
        replaceListItemService = new ReplaceListItemService(createListItemService, deleteListItemService)
    }

    def "test ReplaceListItem() integrity"() {
        given:
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)

        def listItemRequest = new ListItemRequestTO(ItemType.TCIN, tenantrefId1, TestListChannel.WEB.toString(), tcin1, null,
            "test item..", 5, UnitOfMeasure.EACHES, null)
        def listId = UUID.randomUUID()
        def sourceItemId = UUID.randomUUID()
        def cartItemDeleteResponse = cartDataProvider.getCartItemDeleteResponse(listId, sourceItemId)
        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)

        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            listItemRequest.itemTitle, 1, listItemRequest.itemNote, 10, 10, "READY",
            "some-url", "some-image", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)
        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def cartContentResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)
        def listItemMetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse.metadata)

        when:
        def actual = replaceListItemService.replaceListItem(guestId, listId, sourceItemId, 1357L, listItemRequest).block()

        then:
        1 * cartManager.getListCartContents(_,true) >> Mono.just(cartContentResponse)
        1 * cartManager.addMultiCartItems(_ as AddMultiCartItemsRequest, _)  >> { arguments ->  // add new items
            final AddMultiCartItemsRequest addMultiCartItemsRequest = arguments[0]
            assert addMultiCartItemsRequest.cartId == listId
            assert addMultiCartItemsRequest.cartItems.size() == 1
            Mono.just([cartItemResponse])
        }
        1 * cartManager.getCartItem(_,_,) >> Mono.just(cartItemResponse)
        1 * cartManager.deleteCartItem(_) >> Mono.just(cartItemDeleteResponse)
        1 * eventPublisher.publishEvent(CreateListItemNotifyEvent.eventType, _, listId.toString()) >> Mono.just(GroovyMock(RecordMetadata))
        1 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.eventType, _, listId.toString()) >> Mono.just(GroovyMock(RecordMetadata))

        actual.tcin == cartItemResponse.tcin
        actual.itemTitle == cartItemResponse.tenantItemName
        actual.itemNote == cartItemResponse.notes
        actual.price == cartItemResponse.price
        actual.listPrice == cartItemResponse.listPrice
        actual.images == cartItemResponse.images
        actual.requestedQuantity == cartItemResponse.requestedQuantity
        actual.itemType == listItemMetaData.itemType
    }
}
