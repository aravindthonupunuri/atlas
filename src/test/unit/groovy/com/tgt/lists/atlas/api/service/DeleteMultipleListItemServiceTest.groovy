package com.tgt.lists.atlas.api.service


import com.tgt.lists.cart.transport.CartResponse
import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.cart.transport.DeleteMultiCartItemsRequest
import com.tgt.lists.atlas.api.domain.CartManager
import com.tgt.lists.atlas.api.domain.DeleteCartItemsManager
import com.tgt.lists.atlas.api.domain.EventPublisher
import com.tgt.lists.atlas.api.transport.ListItemMetaDataTO
import com.tgt.lists.atlas.api.transport.ListMetaDataTO
import com.tgt.lists.atlas.api.transport.UserItemMetaDataTO
import com.tgt.lists.atlas.api.transport.UserMetaDataTO
import com.tgt.lists.atlas.api.util.ItemIncludeFields
import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.api.util.LIST_STATUS
import com.tgt.lists.atlas.kafka.model.DeleteListItemNotifyEvent
import com.tgt.lists.atlas.util.CartDataProvider
import com.tgt.lists.atlas.util.TestListChannel
import org.apache.kafka.clients.producer.RecordMetadata
import reactor.core.publisher.Mono
import spock.lang.Specification

class DeleteMultipleListItemServiceTest extends Specification {
    
    CartManager cartManager
    EventPublisher eventPublisher
    DeleteCartItemsManager deleteCartItemsManager
    DeleteMultipleListItemService deleteMultipleListItemService
    CartDataProvider cartDataProvider
    String guestId = "1234"

    def setup() {
        cartManager = Mock(CartManager)
        eventPublisher = Mock(EventPublisher)
        deleteCartItemsManager = new DeleteCartItemsManager(cartManager, eventPublisher, true)
        deleteMultipleListItemService = new DeleteMultipleListItemService(deleteCartItemsManager)
        cartDataProvider = new CartDataProvider()
    }

    def "Test delete cart items when no completed cart is present and ItemIncludeFields is ALL"() {
        given:
        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def completedListItemId1 = UUID.randomUUID()
        def completedListItemId2 = UUID.randomUUID()
        def pendingListItemId1 = UUID.randomUUID()
        def pendingListItemId2 = UUID.randomUUID()
        def recordMetadata = GroovyMock(RecordMetadata)

        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "1235"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)

        //*********************************** delete completed cart items **********************************************
        ListMetaDataTO completedCartMetadata = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "completedCart", "completedCart", null, cartDataProvider.getMetaData(completedCartMetadata, new UserMetaDataTO()))

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, completedListItemId1, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)
        def completedCartItemResponse2 = cartDataProvider.getCartItemResponse(completedListId, completedListItemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
            "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        def completedCartContents = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1, completedCartItemResponse2])
        def completedCartItemsDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(completedListId,  [completedListItemId1, completedListItemId2], null)

        //*********************************** delete pending cart items ************************************************
        ListMetaDataTO pendingCartMetadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "pendingCart", "pendingCart", null, cartDataProvider.getMetaData(pendingCartMetadata, new UserMetaDataTO()))

        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, pendingListItemId1, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, pendingListItemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
            "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)

        def pendingCartContents = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])
        def pendingCartItemsDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(listId, [pendingListItemId1, pendingListItemId2], null)

        when:
        def actual = deleteMultipleListItemService.deleteMultipleListItem(guestId, listId, null, ItemIncludeFields.ALL).block()

        then:
        //*********************************** delete completed cart items **********************************************
        1 * cartManager.getCompletedListCart(listId) >> Mono.empty()
        0 * cartManager.getCompletedListCartContents(completedListId, false) >> Mono.just(completedCartContents)
        0 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest ) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0]== completedListItemId1
            assert request.cartItemIds[1]== completedListItemId2
            Mono.just(completedCartItemsDeleteResponse)
        }
        //*********************************** delete pending cart items ************************************************
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(pendingCartContents)
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest ) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0] == pendingListItemId1
            assert request.cartItemIds[1] == pendingListItemId2
            Mono.just(pendingCartItemsDeleteResponse)
        }
        1 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata)

        actual.listId == listId
        actual.successListItemIds.size() == 2
    }

    def "Test exception from getting cart contents from completed cart deleting cart items when ItemIncludeFields is ALL"() {
        given:
        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def completedListItemId1 = UUID.randomUUID()
        def completedListItemId2 = UUID.randomUUID()
        def pendingListItemId1 = UUID.randomUUID()
        def pendingListItemId2 = UUID.randomUUID()

        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "1235"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)

        //*********************************** delete completed cart items **********************************************
        ListMetaDataTO completedCartMetadata = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "completedCart", "completedCart", null, cartDataProvider.getMetaData(completedCartMetadata, new UserMetaDataTO()))
        def completedCartResponseList = [completedCartResponse]
        def completedCartItemsDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(completedListId,  [completedListItemId1, completedListItemId2], null)

        //*********************************** delete pending cart items ************************************************
        ListMetaDataTO pendingCartMetadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "pendingCart", "pendingCart", null, cartDataProvider.getMetaData(pendingCartMetadata, new UserMetaDataTO()))

        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, pendingListItemId1, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, pendingListItemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)

        def pendingCartContents = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])
        def pendingCartItemsDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(listId, [pendingListItemId1, pendingListItemId2], null)

        when:
        deleteMultipleListItemService.deleteMultipleListItem(guestId, listId, null, ItemIncludeFields.ALL).block()

        then:
        //*********************************** delete completed cart items **********************************************
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse)
        1 * cartManager.getListCartContents(completedListId, true) >> Mono.error(new RuntimeException("some exception"))
        0 * cartManager.deleteMultiCartItems(null, false, _ as DeleteMultiCartItemsRequest ) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[2]
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0]== completedListItemId1
            assert request.cartItemIds[1]== completedListItemId2
            Mono.just(completedCartItemsDeleteResponse)
        }
        //*********************************** delete pending cart items ************************************************
        0 * cartManager.getListCartContents(listId, false, null) >> Mono.just(pendingCartContents)
        0 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest ) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[1]
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0] == pendingListItemId1
            assert request.cartItemIds[1] == pendingListItemId2
            Mono.just(pendingCartItemsDeleteResponse)
        }

        thrown(RuntimeException)
    }

    def "Test empty cart contents from completed cart deleting cart items when ItemIncludeFields is ALL"() {
        given:
        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def completedListItemId1 = UUID.randomUUID()
        def completedListItemId2 = UUID.randomUUID()
        def pendingListItemId1 = UUID.randomUUID()
        def pendingListItemId2 = UUID.randomUUID()
        def recordMetadata = GroovyMock(RecordMetadata)

        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "1235"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)

        //*********************************** delete completed cart items **********************************************
        ListMetaDataTO completedCartMetadata = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "completedCart", "completedCart", null, cartDataProvider.getMetaData(completedCartMetadata, new UserMetaDataTO()))
        def completedCartResponseList = [completedCartResponse]
        def completedCartItemsDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(completedListId,  [completedListItemId1, completedListItemId2], null)

        //*********************************** delete pending cart items ************************************************
        ListMetaDataTO pendingCartMetadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "pendingCart", "pendingCart", null, cartDataProvider.getMetaData(pendingCartMetadata, new UserMetaDataTO()))

        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, pendingListItemId1, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, pendingListItemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)

        def pendingCartContents = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])
        def pendingCartItemsDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(listId, [pendingListItemId1, pendingListItemId2], null)

        when:
        def actual = deleteMultipleListItemService.deleteMultipleListItem(guestId, listId, null, ItemIncludeFields.ALL).block()

        then:
        //*********************************** delete completed cart items **********************************************
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse)
        1 * cartManager.getListCartContents(completedListId, true) >> Mono.empty()
        0 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest ) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0]== completedListItemId1
            assert request.cartItemIds[1]== completedListItemId2
            Mono.just(completedCartItemsDeleteResponse)
        }
        //*********************************** delete pending cart items ************************************************
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(pendingCartContents)
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest ) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0] == pendingListItemId1
            assert request.cartItemIds[1] == pendingListItemId2
            Mono.just(pendingCartItemsDeleteResponse)
        }
        1 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata)

        actual.listId == listId
        actual.successListItemIds.size() == 2
    }

    def "Test exception from  deleting completed cart items when ItemIncludeFields is ALL"() {
        given:
        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()

        def completedListItemId1 = UUID.randomUUID()
        def completedListItemId2 = UUID.randomUUID()

        def pendingListItemId1 = UUID.randomUUID()
        def pendingListItemId2 = UUID.randomUUID()

        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "1235"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)

        //*********************************** delete completed cart items **********************************************
        ListMetaDataTO completedCartMetadata = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "completedCart", "completedCart", null, cartDataProvider.getMetaData(completedCartMetadata, new UserMetaDataTO()))
       def completedCartResponseList = [completedCartResponse]

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, completedListItemId1, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)
        def completedCartItemResponse2 = cartDataProvider.getCartItemResponse(completedListId, completedListItemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        def completedCartContents = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1, completedCartItemResponse2])

        //*********************************** delete pending cart items ************************************************
        ListMetaDataTO pendingCartMetadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "pendingCart", "pendingCart", null, cartDataProvider.getMetaData(pendingCartMetadata, new UserMetaDataTO()))

        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, pendingListItemId1, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, pendingListItemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)

        def pendingCartContents = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])
        def pendingCartItemsDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(listId, [pendingListItemId1, pendingListItemId2], null)

        when:
        deleteMultipleListItemService.deleteMultipleListItem(guestId, listId, null, ItemIncludeFields.ALL).block()

        then:
        //*********************************** delete completed cart items **********************************************
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse)
        1 * cartManager.getListCartContents(completedListId, true) >> Mono.just(completedCartContents)
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest ) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0]== completedListItemId1
            assert request.cartItemIds[1]== completedListItemId2
            Mono.error(new RuntimeException("some exception"))
        }
        //*********************************** delete pending cart items ************************************************
        0 * cartManager.getListCartContents(listId, false, null) >> Mono.just(pendingCartContents)
        0 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest ) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[1]
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0] == pendingListItemId1
            assert request.cartItemIds[1] == pendingListItemId2
            Mono.just(pendingCartItemsDeleteResponse)
        }

        thrown(RuntimeException)
    }

    def "Test delete cart items with failed cart items from completed cart when ItemIncludeFields is ALL"() {
        given:
        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def completedListItemId1 = UUID.randomUUID()
        def completedListItemId2 = UUID.randomUUID()
        def pendingListItemId1 = UUID.randomUUID()
        def pendingListItemId2 = UUID.randomUUID()
        def recordMetadata = GroovyMock(RecordMetadata)

        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "1235"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)

        //*********************************** delete completed cart items **********************************************
        ListMetaDataTO completedCartMetadata = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "completedCart", "completedCart", null, cartDataProvider.getMetaData(completedCartMetadata, new UserMetaDataTO()))
        def completedCartResponseList = [completedCartResponse]

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, completedListItemId1, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)
        def completedCartItemResponse2 = cartDataProvider.getCartItemResponse(completedListId, completedListItemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        def completedCartContents = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1, completedCartItemResponse2])
        def completedCartItemsDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(completedListId,  [completedListItemId1], [completedListItemId2])

        //*********************************** delete pending cart items ************************************************
        ListMetaDataTO pendingCartMetadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "pendingCart", "pendingCart", null, cartDataProvider.getMetaData(pendingCartMetadata, new UserMetaDataTO()))

        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, pendingListItemId1, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, pendingListItemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)

        def pendingCartContents = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])
        def pendingCartItemsDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(listId, [pendingListItemId1, pendingListItemId2], null)

        when:
        def actual = deleteMultipleListItemService.deleteMultipleListItem(guestId, listId, null, ItemIncludeFields.ALL).block()

        then:
        //*********************************** delete completed cart items **********************************************
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse)
        1 * cartManager.getListCartContents(completedListId, true) >> Mono.just(completedCartContents)
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest ) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0]== completedListItemId1
            assert request.cartItemIds[1]== completedListItemId2
            Mono.just(completedCartItemsDeleteResponse)
        }
        1 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata)
        //*********************************** delete pending cart items ************************************************
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(pendingCartContents)
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest ) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0] == pendingListItemId1
            assert request.cartItemIds[1] == pendingListItemId2
            Mono.just(pendingCartItemsDeleteResponse)
        }
        1 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata)

        actual.listId == listId
        actual.successListItemIds.size() == 3
        actual.failedListItemIds[0] == completedListItemId2
    }

    def "Test exception from  getting cart contents from pending cart deleting cart items when ItemIncludeFields is ALL"() {
        given:
        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def completedListItemId1 = UUID.randomUUID()
        def completedListItemId2 = UUID.randomUUID()
        def recordMetadata = GroovyMock(RecordMetadata)

        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "1235"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)

        //*********************************** delete completed cart items **********************************************
        ListMetaDataTO completedCartMetadata = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "completedCart", "completedCart", null, cartDataProvider.getMetaData(completedCartMetadata, new UserMetaDataTO()))
        def completedCartResponseList = [completedCartResponse]

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, completedListItemId1, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)
        def completedCartItemResponse2 = cartDataProvider.getCartItemResponse(completedListId, completedListItemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        def completedCartContents = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1, completedCartItemResponse2])
        def completedCartItemsDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(completedListId,  [completedListItemId1, completedListItemId2], null)

        when:
        deleteMultipleListItemService.deleteMultipleListItem(guestId, listId, null, ItemIncludeFields.ALL).block()

        then:
        //*********************************** delete completed cart items **********************************************
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse)
        1 * cartManager.getListCartContents(completedListId, true) >> Mono.just(completedCartContents)
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest ) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0]== completedListItemId1
            assert request.cartItemIds[1]== completedListItemId2
            Mono.just(completedCartItemsDeleteResponse)
        }
        1 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata)
        //*********************************** delete pending cart items ************************************************
        1 * cartManager.getListCartContents(listId, true) >>  Mono.error(new RuntimeException("some exception"))
        0 * cartManager.deleteMultiCartItems(null, false, _ as DeleteMultiCartItemsRequest )

        thrown(RuntimeException)
    }

    def "Test empty cart contents from pending cart deleting cart items when ItemIncludeFields is ALL"() {
        given:
        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def completedListItemId1 = UUID.randomUUID()
        def completedListItemId2 = UUID.randomUUID()
        def recordMetadata = GroovyMock(RecordMetadata)

        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "1235"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)

        //*********************************** delete completed cart items **********************************************
        ListMetaDataTO completedCartMetadata = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "completedCart", "completedCart", null, cartDataProvider.getMetaData(completedCartMetadata, new UserMetaDataTO()))
        def completedCartResponseList = [completedCartResponse]

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, completedListItemId1, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)
        def completedCartItemResponse2 = cartDataProvider.getCartItemResponse(completedListId, completedListItemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        def completedCartContents = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1, completedCartItemResponse2])
        def completedCartItemsDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(completedListId,  [completedListItemId1, completedListItemId2], null)

        when:
        def actual = deleteMultipleListItemService.deleteMultipleListItem(guestId, listId, null, ItemIncludeFields.ALL).block()

        then:
        //*********************************** delete completed cart items **********************************************
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse)
        1 * cartManager.getListCartContents(completedListId, true) >> Mono.just(completedCartContents)
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest ) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0]== completedListItemId1
            assert request.cartItemIds[1]== completedListItemId2
            Mono.just(completedCartItemsDeleteResponse)
        }
        1 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata)
        //*********************************** delete pending cart items ************************************************
        1 * cartManager.getListCartContents(listId, true) >> Mono.empty()
        0 * cartManager.deleteMultiCartItems(null, false, _ as DeleteMultiCartItemsRequest )

        actual.listId == listId
        actual.successListItemIds.size() == 2
    }

    def "Test exception from  deleting pending cart items when ItemIncludeFields is ALL"() {
        given:
        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def completedListItemId1 = UUID.randomUUID()
        def completedListItemId2 = UUID.randomUUID()
        def pendingListItemId1 = UUID.randomUUID()
        def pendingListItemId2 = UUID.randomUUID()
        def recordMetadata = GroovyMock(RecordMetadata)

        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "1235"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)

        //*********************************** delete completed cart items **********************************************
        ListMetaDataTO completedCartMetadata = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "completedCart", "completedCart", null, cartDataProvider.getMetaData(completedCartMetadata, new UserMetaDataTO()))
        def completedCartResponseList = [completedCartResponse]

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, completedListItemId1, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)
        def completedCartItemResponse2 = cartDataProvider.getCartItemResponse(completedListId, completedListItemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        def completedCartContents = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1, completedCartItemResponse2])
        def completedCartItemsDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(completedListId,  [completedListItemId1, completedListItemId2], null)

        //*********************************** delete pending cart items ************************************************
        ListMetaDataTO pendingCartMetadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "pendingCart", "pendingCart", null, cartDataProvider.getMetaData(pendingCartMetadata, new UserMetaDataTO()))

        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, pendingListItemId1, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, pendingListItemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)

        def pendingCartContents = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])

        when:
        deleteMultipleListItemService.deleteMultipleListItem(guestId, listId, null, ItemIncludeFields.ALL).block()

        then:
        //*********************************** delete completed cart items **********************************************
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse)
        1 * cartManager.getListCartContents(completedListId, true) >> Mono.just(completedCartContents)
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest ) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0]== completedListItemId1
            assert request.cartItemIds[1]== completedListItemId2
            Mono.just(completedCartItemsDeleteResponse)
        }
        1 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata)
        //*********************************** delete pending cart items ************************************************
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(pendingCartContents)
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest ) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0] == pendingListItemId1
            assert request.cartItemIds[1] == pendingListItemId2
            Mono.error(new RuntimeException("some exception"))
        }

        thrown(RuntimeException)
    }

    def "Test delete cart items with failed cart items from pending cart when ItemIncludeFields is ALL"() {
        given:
        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def completedListItemId1 = UUID.randomUUID()
        def completedListItemId2 = UUID.randomUUID()
        def pendingListItemId1 = UUID.randomUUID()
        def pendingListItemId2 = UUID.randomUUID()
        def recordMetadata = GroovyMock(RecordMetadata)

        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "1235"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)

        //*********************************** delete completed cart items **********************************************
        ListMetaDataTO completedCartMetadata = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "completedCart", "completedCart", null, cartDataProvider.getMetaData(completedCartMetadata, new UserMetaDataTO()))
        def completedCartResponseList = [completedCartResponse]

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, completedListItemId1, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)
        def completedCartItemResponse2 = cartDataProvider.getCartItemResponse(completedListId, completedListItemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        def completedCartContents = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1, completedCartItemResponse2])
        def completedCartItemsDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(completedListId,  [completedListItemId1, completedListItemId2], null)

        //*********************************** delete pending cart items ************************************************
        ListMetaDataTO pendingCartMetadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "pendingCart", "pendingCart", null, cartDataProvider.getMetaData(pendingCartMetadata, new UserMetaDataTO()))

        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, pendingListItemId1, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, pendingListItemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)

        def pendingCartContents = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])
        def pendingCartItemsDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(listId, [pendingListItemId1], [pendingListItemId2])

        when:
        def actual = deleteMultipleListItemService.deleteMultipleListItem(guestId, listId, null, ItemIncludeFields.ALL).block()

        then:
        //*********************************** delete completed cart items **********************************************
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse)
        1 * cartManager.getListCartContents(completedListId, true) >> Mono.just(completedCartContents)
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest ) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0]== completedListItemId1
            assert request.cartItemIds[1]== completedListItemId2
            Mono.just(completedCartItemsDeleteResponse)
        }
        1 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata)
        //*********************************** delete pending cart items ************************************************
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(pendingCartContents)
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest ) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0] == pendingListItemId1
            assert request.cartItemIds[1] == pendingListItemId2
            Mono.just(pendingCartItemsDeleteResponse)
        }
        1 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata)

        actual.listId == listId
        actual.successListItemIds.size() == 3
        actual.failedListItemIds[0] == pendingListItemId2
    }

    def "Test deleteMultipleListItem() when ItemIncludeFields is PENDING"() {
        given:
        def listId = UUID.randomUUID()
        def pendingListItemId1 = UUID.randomUUID()
        def pendingListItemId2 = UUID.randomUUID()
        def recordMetadata = GroovyMock(RecordMetadata)

        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "1235"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)

        ListMetaDataTO pendingCartMetadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "pendingCart", "pendingCart", null, cartDataProvider.getMetaData(pendingCartMetadata, new UserMetaDataTO()))

        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, pendingListItemId1, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, pendingListItemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)

        def pendingCartContents = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])
        def pendingCartItemsDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(listId, [pendingListItemId1, pendingListItemId2], null)

        when:
        def actual = deleteMultipleListItemService.deleteMultipleListItem(guestId, listId, null, ItemIncludeFields.PENDING).block()

        then:
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(pendingCartContents)
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest ) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0] == pendingListItemId1
            assert request.cartItemIds[1] == pendingListItemId2
            Mono.just(pendingCartItemsDeleteResponse)
        }
        1 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata)

        actual.listId == listId
        actual.successListItemIds.size() == 2
    }

    def "Test deleteMultipleListItem() when ItemIncludeFields is COMPLETED"() {
        given:
        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def completedListItemId1 = UUID.randomUUID()
        def completedListItemId2 = UUID.randomUUID()
        def recordMetadata = GroovyMock(RecordMetadata)

        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "1235"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)

        ListMetaDataTO completedCartMetadata = new ListMetaDataTO(false, LIST_STATUS.COMPLETED)
        CartResponse completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "completedCart", "completedCart", null, cartDataProvider.getMetaData(completedCartMetadata, new UserMetaDataTO()))
        def completedCartResponseList = [completedCartResponse]

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, completedListItemId1, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)
        def completedCartItemResponse2 = cartDataProvider.getCartItemResponse(completedListId, completedListItemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        def completedCartContents = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1, completedCartItemResponse2])
        def completedCartItemsDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(completedListId,  [completedListItemId1, completedListItemId2], null)

        when:
        def actual = deleteMultipleListItemService.deleteMultipleListItem(guestId, listId, null, ItemIncludeFields.COMPLETED).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse)
        1 * cartManager.getListCartContents(completedListId, true) >> Mono.just(completedCartContents)
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest ) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0]== completedListItemId1
            assert request.cartItemIds[1]== completedListItemId2
            Mono.just(completedCartItemsDeleteResponse)
        }
        1 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata)

        actual.listId == listId
        actual.successListItemIds.size() == 2
    }

    def "Test deleteMultipleListItem() when cartItemIds to be deleted are passed as part of request"() {
        given:
        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def completedListItemId1 = UUID.randomUUID()
        def completedListItemId2 = UUID.randomUUID()
        def pendingListItemId1 = UUID.randomUUID()
        def pendingListItemId2 = UUID.randomUUID()
        def recordMetadata = GroovyMock(RecordMetadata)

        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "1235"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)

        List cartItemIds = [completedListItemId1, completedListItemId2, pendingListItemId1,  pendingListItemId2]

        //*********************************** delete items from pending cart *******************************************
        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, pendingListItemId1, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, pendingListItemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)

        ListMetaDataTO pendingCartMetadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "pendingCart", "pendingCart", null, cartDataProvider.getMetaData(pendingCartMetadata, new UserMetaDataTO()))
        def pendingCartContents = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])
        def pendingCartItemsDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(listId, [pendingListItemId1, pendingListItemId2], null)

        //*********************************** delete remaining items from completed cart *******************************
        ListMetaDataTO completedCartMetadata = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "completedCart", "completedCart", null, cartDataProvider.getMetaData(completedCartMetadata, new UserMetaDataTO()))
        def completedCartResponseList = [completedCartResponse]

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, completedListItemId1, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)
        def completedCartItemResponse2 = cartDataProvider.getCartItemResponse(completedListId, completedListItemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        def completedCartContents = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1, completedCartItemResponse2])
        def completedCartItemsDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(completedListId,  [completedListItemId1, completedListItemId2], null)

        when:
        def actual = deleteMultipleListItemService.deleteMultipleListItem(guestId, listId, cartItemIds, ItemIncludeFields.ALL).block()

        then:
        //*********************************** delete items from pending cart ************************************************
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest ) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0] == pendingListItemId1
            assert request.cartItemIds[1] == pendingListItemId2
            Mono.just(pendingCartItemsDeleteResponse)
        }
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(pendingCartContents)
        1 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata)

        //*********************************** delete remaining items from completed cart *******************************
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse)
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest ) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0]== completedListItemId1
            assert request.cartItemIds[1]== completedListItemId2
            Mono.just(completedCartItemsDeleteResponse)
        }
        1 * cartManager.getListCartContents(completedListId, true) >> Mono.just(completedCartContents)
        1 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata)

        actual.listId == listId
        actual.successListItemIds.size() == 4
    }

    def "Test deleteMultipleListItem() when only pending cartItemIds to be deleted are passed as part of request"() {
        given:
        def listId = UUID.randomUUID()
        def pendingListItemId1 = UUID.randomUUID()
        def pendingListItemId2 = UUID.randomUUID()
        def recordMetadata = GroovyMock(RecordMetadata)

        List cartItemIds = [pendingListItemId1, pendingListItemId2]

        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "1235"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)

        //*********************************** delete items from pending cart *******************************************
        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, pendingListItemId1, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, pendingListItemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)

        ListMetaDataTO pendingCartMetadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "pendingCart", "pendingCart", null, cartDataProvider.getMetaData(pendingCartMetadata, new UserMetaDataTO()))
        def pendingCartContents = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])
        def pendingCartItemsDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(listId, [pendingListItemId1, pendingListItemId2], null)

        when:
        def actual = deleteMultipleListItemService.deleteMultipleListItem(guestId, listId, cartItemIds, ItemIncludeFields.ALL).block()

        then:
        //*********************************** delete items from pending cart ************************************************
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(pendingCartContents)
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest ) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0] == pendingListItemId1
            assert request.cartItemIds[1] == pendingListItemId2
            Mono.just(pendingCartItemsDeleteResponse)
        }
        1 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata)

        //*********************************** delete remaining items from completed cart *******************************
        0 * cartManager.getCompletedListCart(listId)

        actual.listId == listId
        actual.successListItemIds.size() == 2
        actual.failedListItemIds == null
    }

    def "Test deleteMultipleListItem() when only completed cartItemIds to be deleted are passed as part of request"() {
        given:
        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def completedListItemId1 = UUID.randomUUID()
        def completedListItemId2 = UUID.randomUUID()
        def recordMetadata = GroovyMock(RecordMetadata)

        List cartItemIds = [completedListItemId1, completedListItemId2]

        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "1235"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)

        //*********************************** delete remaining items from completed cart *******************************
        ListMetaDataTO completedCartMetadata = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "completedCart", "completedCart", null, cartDataProvider.getMetaData(completedCartMetadata, new UserMetaDataTO()))

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, completedListItemId1, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)
        def completedCartItemResponse2 = cartDataProvider.getCartItemResponse(completedListId, completedListItemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)


        def completedCartContents = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1, completedCartItemResponse2])
        def completedCartItemsDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(completedListId,  [completedListItemId1, completedListItemId2], null)

        when:
        def actual = deleteMultipleListItemService.deleteMultipleListItem(guestId, listId, cartItemIds, ItemIncludeFields.ALL).block()

        then:
        //*********************************** delete remaining items from completed cart *******************************
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse)
        1 * cartManager.getListCartContents(listId, true) >> Mono.empty()
        1 * cartManager.getListCartContents(completedListId, true) >> Mono.just(completedCartContents)
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest ) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0]== completedListItemId1
            assert request.cartItemIds[1]== completedListItemId2
            Mono.just(completedCartItemsDeleteResponse)
        }
        1 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata)

        actual.listId == listId
        actual.successListItemIds.size() == 2
        actual.failedListItemIds.isEmpty()
    }

    def "Test deleteMultipleListItem() when one failed cartItem from pending cart and completed cart"() {
        given:
        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def completedListItemId1 = UUID.randomUUID()
        def completedListItemId2 = UUID.randomUUID()
        def pendingListItemId1 = UUID.randomUUID()
        def pendingListItemId2 = UUID.randomUUID()
        def recordMetadata = GroovyMock(RecordMetadata)

        List cartItemIds = [completedListItemId1, completedListItemId2, pendingListItemId1, pendingListItemId2]

        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "1235"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)

        //*********************************** delete items from pending cart *******************************************
        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, pendingListItemId1, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, pendingListItemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)


        ListMetaDataTO pendingCartMetadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "pendingCart", "pendingCart", null, cartDataProvider.getMetaData(pendingCartMetadata, new UserMetaDataTO()))
        def pendingCartContents = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])
        def pendingCartItemsDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(listId, [pendingListItemId1], [pendingListItemId2])
        //*********************************** delete remaining items from completed cart *******************************
        ListMetaDataTO completedCartMetadata = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "completedCart", "completedCart", null, cartDataProvider.getMetaData(completedCartMetadata, new UserMetaDataTO()))
        def completedCartResponseList = [completedCartResponse]

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, completedListItemId1, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)
        def completedCartItemResponse2 = cartDataProvider.getCartItemResponse(completedListId, completedListItemId2, tenantrefId2, TestListChannel.WEB.toString(), tcin2,
                "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)


        def completedCartContents = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1, completedCartItemResponse2])
        def completedCartItemsDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(completedListId,  [completedListItemId1], [completedListItemId2])

        when:
        def actual = deleteMultipleListItemService.deleteMultipleListItem(guestId, listId, cartItemIds, ItemIncludeFields.ALL).block()

        then:
        //*********************************** delete items from pending cart ************************************************
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest ) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0] == pendingListItemId1
            assert request.cartItemIds[1] == pendingListItemId2
            Mono.just(pendingCartItemsDeleteResponse)
        }
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(pendingCartContents)
        1 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata)

        //*********************************** delete remaining items from completed cart *******************************
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse)
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest ) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartItemIds.size() == 2
            assert request.cartItemIds[0]== completedListItemId1
            assert request.cartItemIds[1]== completedListItemId2
            Mono.just(completedCartItemsDeleteResponse)
        }
        1 * cartManager.getListCartContents(completedListId, true) >> Mono.just(completedCartContents)
        1 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata)

        actual.listId == listId
        actual.successListItemIds.size() == 2
        actual.failedListItemIds.size() == 2
    }
}
