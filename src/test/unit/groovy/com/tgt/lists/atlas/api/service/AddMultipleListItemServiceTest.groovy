package com.tgt.lists.atlas.api.service


import com.tgt.lists.cart.transport.AddMultiCartItemsRequest
import com.tgt.lists.cart.transport.DeleteMultiCartItemsRequest
import com.tgt.lists.cart.transport.UpdateMultiCartItemsRequest
import com.tgt.lists.atlas.api.domain.*
import com.tgt.lists.atlas.api.transport.ListItemMetaDataTO
import com.tgt.lists.atlas.api.transport.ListItemRequestTO
import com.tgt.lists.atlas.api.transport.UserItemMetaDataTO
import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.api.util.UnitOfMeasure
import com.tgt.lists.atlas.util.CartDataProvider
import com.tgt.lists.atlas.util.TestListChannel
import org.apache.kafka.clients.producer.RecordMetadata
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.LocalDateTime

class AddMultipleListItemServiceTest extends Specification {
    
    CartManager cartManager
    EventPublisher eventPublisher
    AddMultiItemsManager addMultiItemsManager
    DeduplicationManager deduplicationManager
    DeleteCartItemsManager deleteCartItemsManager
    UpdateCartItemsManager updateCartItemsManager
    CreateCartItemsManager createCartItemsManager
    AddMultipleListItemService addMultipleListItemService
    CartDataProvider cartDataProvider

    String guestId = "1234"

    def setup() {
        cartManager = Mock(CartManager)
        eventPublisher = Mock(EventPublisher)
        updateCartItemsManager = new UpdateCartItemsManager(cartManager, eventPublisher)
        createCartItemsManager = new CreateCartItemsManager(cartManager, eventPublisher)
        deleteCartItemsManager = new DeleteCartItemsManager(cartManager, eventPublisher, true)
        deduplicationManager = new DeduplicationManager(cartManager, updateCartItemsManager, deleteCartItemsManager, true, 10, 10, false)
        addMultiItemsManager = new AddMultiItemsManager(deduplicationManager, createCartItemsManager)
        addMultipleListItemService = new AddMultipleListItemService(addMultiItemsManager)
        cartDataProvider = new CartDataProvider()
    }

    def "test createListItem() integrity"() {
        given:
        def listId = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "5678"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)
        def tcin3 = "1000"
        def tenantrefId3 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin3)
        def tcin4 = "9999"
        def tenantrefId4 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin4)
        def tcin5 = "1111"
        def tenantrefId5 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin5)

        def listItemRequest1 = new ListItemRequestTO(ItemType.TCIN, tenantrefId1, TestListChannel.WEB.toString(), tcin1, null,
                "test item", null, UnitOfMeasure.EACHES, null)
        def listItemRequest2 = new ListItemRequestTO(ItemType.TCIN, tenantrefId2, TestListChannel.WEB.toString(), tcin2, null,
                "test item", null, UnitOfMeasure.EACHES, null)
        def listItemRequest3 = new ListItemRequestTO(ItemType.TCIN, tenantrefId3, TestListChannel.WEB.toString(), tcin3, null,
                "test item", null, UnitOfMeasure.EACHES, null)
        def listItemRequest4 = new ListItemRequestTO(ItemType.TCIN, tenantrefId4, TestListChannel.WEB.toString(), tcin4, null,
                "test item", null, UnitOfMeasure.EACHES, null)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID() , tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 2, "notes1", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID() , tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 3, "notes2", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartItemResponse3 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID() , tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 1, "notes3", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartItemResponse4 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID() , tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 2, "notes4", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartItemResponse5 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID() , tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 3, "notes5", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartItemResponse6 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID() , tenantrefId5, TestListChannel.WEB.toString(), tcin5,
                "title", 1, "notes6", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartItemResponse7 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID() , tenantrefId5, TestListChannel.WEB.toString(), tcin5,
                "title", 1, "notes7", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()),
                null, LocalDateTime.now(), null)
        def pendingCartContentResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse,
                [pendingCartItemResponse1, pendingCartItemResponse2, pendingCartItemResponse3, pendingCartItemResponse4,
                 pendingCartItemResponse5, pendingCartItemResponse6, pendingCartItemResponse7])
        def dedupedPendingCartItem1 = cartDataProvider.getCartItemResponse(listId, pendingCartItemResponse1.cartItemId, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "title", 6, "notes1\nnotes2", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()), null, null, null)
        def dedupedPendingCartItem2 = cartDataProvider.getCartItemResponse(listId, pendingCartItemResponse3.cartItemId, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "title", 7, "notes3\nnotes4\nnotes5", 0, 0, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()), null, null, null)
        def updateMultiCartItemsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [dedupedPendingCartItem1, dedupedPendingCartItem2])
        def multiCartItemDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(listId,
                [pendingCartItemResponse2.cartItemId, pendingCartItemResponse4.cartItemId, pendingCartItemResponse5.cartItemId], null)
        def newCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), tenantrefId3, TestListChannel.WEB.toString(), tcin3,
                "itemTitle1", 1, "newitemNote1",10, 10, "Stand Alone",
                "READY", "some-url", "some-image",
                cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()), null, null, null)
        def newCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), tenantrefId4, TestListChannel.WEB.toString(), tcin4,
                "itemTitle2", 1, "newitemNote2",10, 10, "Stand Alone",
                "READY", "some-url", "some-image",
                cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()), null, null, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse,
                [dedupedPendingCartItem1, dedupedPendingCartItem2, newCartItemResponse1, newCartItemResponse2])
        def recordMetadata = GroovyMock(RecordMetadata)
        when:
        def actual = addMultipleListItemService.addMultipleListItem(guestId, listId, 1357L,
                [listItemRequest1, listItemRequest2, listItemRequest3, listItemRequest4]).block()
        then:
        1 * cartManager.getListCartContents(listId,true) >> Mono.just(pendingCartContentResponse) //  dedup call
        1 * cartManager.updateMultiCartItems(_ as UpdateMultiCartItemsRequest) >> { arguments ->
            final UpdateMultiCartItemsRequest updateMultiCartItemsRequest = arguments[0]
            assert updateMultiCartItemsRequest.cartId == listId
            assert updateMultiCartItemsRequest.cartItems.size() == 2
            assert updateMultiCartItemsRequest.cartItems[0].requestedQuantity == pendingCartItemResponse1.requestedQuantity + pendingCartItemResponse2.requestedQuantity + 1
            assert updateMultiCartItemsRequest.cartItems[1].requestedQuantity == pendingCartItemResponse3.requestedQuantity + pendingCartItemResponse4.requestedQuantity + pendingCartItemResponse5.requestedQuantity + 1
            Mono.just(updateMultiCartItemsResponse)
        } // update deduped items
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartItemIds.size() == 3
            Mono.just(multiCartItemDeleteResponse)
        } // delete duplicate items
        1 * cartManager.addMultiCartItems(_ as AddMultiCartItemsRequest, _)  >> { arguments ->
            final AddMultiCartItemsRequest addMultiCartItemsRequest = arguments[0]
            assert addMultiCartItemsRequest.cartId == listId
            assert addMultiCartItemsRequest.cartItems.size() == 2
            Mono.just([newCartItemResponse1, newCartItemResponse2])
        }
        5 * eventPublisher.publishEvent(_,_,_) >> Mono.just(recordMetadata)
        actual.items.size() == 4
    }

    def "test createListItem() with duplicate items in request"() {
        given:
        def listId = UUID.randomUUID()
        def tcin1 = "1234"
        def tcinTenantRefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def gItem1 = "item2"
        def gItemTenantrefId1 = cartDataProvider.getTenantRefId(ItemType.GENERIC_ITEM, gItem1)

        def listItemRequest1 = new ListItemRequestTO(ItemType.TCIN, tcinTenantRefId1, TestListChannel.WEB.toString(), tcin1, null,
                "test item", null, UnitOfMeasure.EACHES, null)

        def listItemRequest2 = new ListItemRequestTO(ItemType.GENERIC_ITEM, gItemTenantrefId1, TestListChannel.WEB.toString(), null, gItem1,
                "test item", null, UnitOfMeasure.EACHES, null)

        def listItemRequest3 = new ListItemRequestTO(ItemType.GENERIC_ITEM, gItemTenantrefId1, TestListChannel.WEB.toString(), null, gItem1,
                "test item", null, UnitOfMeasure.EACHES, null)

        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def pendingCartContentResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [])

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), tcinTenantRefId1, TestListChannel.WEB.toString(), tcin1,
                "itemTitle1", 1, "note1",10, 10, "Stand Alone",
                "READY", "some-url", "some-image",
                cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.GENERIC_ITEM, LIST_ITEM_STATE.PENDING)
        def cartItemResponse2 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), gItemTenantrefId1, TestListChannel.WEB.toString(), null,
                gItem1, 1, "note2",10, 10, "Stand Alone",
                "READY", "some-url", "some-image",
                cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)
        def recordMetadata = GroovyMock(RecordMetadata)

        when:
        addMultipleListItemService.addMultipleListItem(guestId, listId, 1357L,
                [listItemRequest1, listItemRequest2, listItemRequest3]).block()

        then:
        1 * cartManager.getListCartContents(listId,true) >> Mono.just(pendingCartContentResponse) //  dedup call
        1 * cartManager.addMultiCartItems(_ as AddMultiCartItemsRequest, _)  >> { arguments ->
            final AddMultiCartItemsRequest addMultiCartItemsRequest = arguments[0]
            assert addMultiCartItemsRequest.cartId == listId
            assert addMultiCartItemsRequest.cartItems.size() == 2
            Mono.just([cartItemResponse1, cartItemResponse2])
        }
        2 * eventPublisher.publishEvent(_,_,_) >> Mono.just(recordMetadata)
    }
}
