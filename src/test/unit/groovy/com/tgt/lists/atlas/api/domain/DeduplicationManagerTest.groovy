package com.tgt.lists.atlas.api.domain


import com.tgt.lists.cart.transport.DeleteMultiCartItemsRequest
import com.tgt.lists.cart.transport.UpdateMultiCartItemsRequest
import com.tgt.lists.common.components.exception.BadRequestException
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

class DeduplicationManagerTest extends Specification {
    
    CartManager cartManager
    EventPublisher eventPublisher
    DeduplicationManager deduplicationManager
    UpdateCartItemsManager updateCartItemsManager
    DeleteCartItemsManager deleteCartItemsManager
    CartDataProvider cartDataProvider
    String guestId = "1234"
    Long locationId = 1375L

    def setup() {
        eventPublisher = Mock(EventPublisher)
        cartManager = Mock(CartManager)
        updateCartItemsManager = new UpdateCartItemsManager(cartManager, eventPublisher)
        deleteCartItemsManager = new DeleteCartItemsManager(cartManager, eventPublisher, true)
        deduplicationManager = new DeduplicationManager(cartManager, updateCartItemsManager, deleteCartItemsManager,
                true, 5, 5, false)
        cartDataProvider = new CartDataProvider()
    }

    def "Test checkIfItemPreExist() for list with no preexisting items, so skipping dedup process"() {
        given:
        def listItemRequest = new ListItemRequestTO(ItemType.TCIN, "tcn1234",  TestListChannel.WEB.toString(),"1234", null,
            "itemNote", 1, UnitOfMeasure.EACHES, null)
        Map<String, ListItemRequestTO> newItemsMap = [ "tcn1234" : listItemRequest ] as LinkedHashMap

        def listId = UUID.randomUUID()
        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def cartContentResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)

        when:
        def actual = deduplicationManager.updateDuplicateItems(listId, listId, locationId, newItemsMap, LIST_ITEM_STATE.PENDING).block()

        then:
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(cartContentResponse)

        actual.first.isEmpty()
        actual.second.isEmpty()
        actual.third.isEmpty()
    }

    def "Test checkIfItemPreExist() with no matching pending items"() {
        given:
        def listItemRequest = new ListItemRequestTO(ItemType.TCIN, "tcn1234",  TestListChannel.WEB.toString(), "1234", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        Map<String, ListItemRequestTO> newItemsMap = [ "tcn1234" : listItemRequest ] as LinkedHashMap
        def listId = UUID.randomUUID()
        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), "tcn5678", TestListChannel.WEB.toString(),
                "5678", listItemRequest.itemTitle, 1, listItemRequest.itemNote, 10,
                10,"Stand Alone", "READY", "some-url",
                "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()),
                null, null, null)
        def cartContentResponse = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse])

        when:
        def actual = deduplicationManager.updateDuplicateItems(listId, listId, locationId, newItemsMap, LIST_ITEM_STATE.PENDING).block()

        then:
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(cartContentResponse)

        actual.first.size() == 1
        actual.second.isEmpty()
        actual.third.isEmpty()
    }

    def "Test checkIfItemPreExist() with no matching item type to dedup, so skipping dedup"() {
        given:
        def listItemRequest = new ListItemRequestTO(ItemType.TCIN, "tcn1234",  TestListChannel.WEB.toString(), "1234", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        Map<String, ListItemRequestTO> newItemsMap = [ "tcn1234" : listItemRequest ] as LinkedHashMap
        def listId = UUID.randomUUID()
        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.GENERIC_ITEM, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), "itm5678", TestListChannel.WEB.toString(),
                null, listItemRequest.itemTitle, 1, listItemRequest.itemNote, 10,
                10,"Stand Alone", "READY", "some-url",
                "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()),
                null, null, null)
        def cartContentResponse = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse])

        when:
        def actual = deduplicationManager.updateDuplicateItems(listId, listId, locationId, newItemsMap, LIST_ITEM_STATE.PENDING).block()

        then:
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(cartContentResponse)

        actual.first.size() == 1
        actual.second.isEmpty()
        actual.third.isEmpty()
    }

    def "Test updateDuplicateItems() exceeding max pending items"() {
        given:
        def listItemRequest1 = new ListItemRequestTO(ItemType.TCIN, "tcn1234",  TestListChannel.WEB.toString(), "1234", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest2 = new ListItemRequestTO(ItemType.TCIN, "tcn2345",  TestListChannel.WEB.toString(), "2345", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest3 = new ListItemRequestTO(ItemType.TCIN, "tcn3456",  TestListChannel.WEB.toString(), "3456", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest4 = new ListItemRequestTO(ItemType.TCIN, "tcn4567",  TestListChannel.WEB.toString(), "4567", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest5 = new ListItemRequestTO(ItemType.TCIN, "tcn2222",  TestListChannel.WEB.toString(), "2222", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)

        Map<String, ListItemRequestTO> newItemsMap = [ "tcn1234" : listItemRequest1, "tcn2345" : listItemRequest2,
                                                       "tcn3456" : listItemRequest3, "tcn4567" : listItemRequest4,
                                                       "tcn2222" : listItemRequest5] as LinkedHashMap
        def listId = UUID.randomUUID()

        ListItemMetaDataTO newItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), "tcn1111", TestListChannel.WEB.toString(),
                "1111", "new item", 1, "newItemNote", 10, 10,
                "Stand Alone", "READY", "some-url", "some-image",
                cartDataProvider.getItemMetaData(newItemMetaData, new UserItemMetaDataTO()), null,
                null, null)
        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def cartContentResponse = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse])

        when:
        deduplicationManager.updateDuplicateItems(listId, listId, locationId, newItemsMap, LIST_ITEM_STATE.PENDING).block()

        then:
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(cartContentResponse)

        thrown(BadRequestException)
    }

    def "Test updateDuplicateItems() exceeding max pending items but rolling update turned on"() {
        given:
        def listItemRequest1 = new ListItemRequestTO(ItemType.TCIN, "tcn1234",  TestListChannel.WEB.toString(), "1234", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest2 = new ListItemRequestTO(ItemType.TCIN, "tcn2345",  TestListChannel.WEB.toString(), "2345", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest3 = new ListItemRequestTO(ItemType.TCIN, "tcn3456",  TestListChannel.WEB.toString(), "3456", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest4 = new ListItemRequestTO(ItemType.TCIN, "tcn4567",  TestListChannel.WEB.toString(), "4567", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest5 = new ListItemRequestTO(ItemType.TCIN, "tcn2222",  TestListChannel.WEB.toString(), "2222", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)

        Map<String, ListItemRequestTO> newItemsMap = [ "tcn1234" : listItemRequest1, "tcn2345" : listItemRequest2,
                                                       "tcn3456" : listItemRequest3, "tcn4567" : listItemRequest4,
                                                       "tcn2222" : listItemRequest5] as LinkedHashMap
        def listId = UUID.randomUUID()

        ListItemMetaDataTO newItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), "tcn1111", TestListChannel.WEB.toString(),
                "1111", "new item", 1, "newItemNote", 10, 10,
                "Stand Alone", "READY", "some-url", "some-image",
                cartDataProvider.getItemMetaData(newItemMetaData, new UserItemMetaDataTO()), null,
                null, null)
        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def cartContentResponse = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse])
        def multiCartItemDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(listId, [cartItemResponse.cartItemId], null)
        def recordMetadata = GroovyMock(RecordMetadata)

        deduplicationManager = new DeduplicationManager(cartManager, updateCartItemsManager, deleteCartItemsManager,
                true, 5, 5, true) // turning on rolling update
        when:
        def actual = deduplicationManager.updateDuplicateItems(listId, listId, locationId, newItemsMap, LIST_ITEM_STATE.PENDING).block()

        then:
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(cartContentResponse)
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartItemIds.size() == 1
            assert request.cartItemIds.first() ==  cartItemResponse.cartItemId
            Mono.just(multiCartItemDeleteResponse)
        } // deleting stale items since rolling update is turned on
        1 * eventPublisher.publishEvent(_,_,_) >> Mono.just(recordMetadata)

        actual.first.size() == 1
        actual.second.isEmpty()
        actual.third.isEmpty()
    }

    def "Test updateDuplicateItems() exceeding max pending items but rolling update turned on and dedupe turned off"() {
        given:
        def listItemRequest1 = new ListItemRequestTO(ItemType.TCIN, "tcn1234",  TestListChannel.WEB.toString(), "1234", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest2 = new ListItemRequestTO(ItemType.TCIN, "tcn2345",  TestListChannel.WEB.toString(), "2345", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest3 = new ListItemRequestTO(ItemType.TCIN, "tcn3456",  TestListChannel.WEB.toString(), "3456", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest4 = new ListItemRequestTO(ItemType.TCIN, "tcn4567",  TestListChannel.WEB.toString(), "4567", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest5 = new ListItemRequestTO(ItemType.TCIN, "tcn2222",  TestListChannel.WEB.toString(), "2222", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)

        Map<String, ListItemRequestTO> newItemsMap = [ "tcn1234" : listItemRequest1, "tcn2345" : listItemRequest2,
                                                       "tcn3456" : listItemRequest3, "tcn4567" : listItemRequest4,
                                                       "tcn2222" : listItemRequest5] as LinkedHashMap
        def listId = UUID.randomUUID()

        ListItemMetaDataTO newItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), "tcn1234", TestListChannel.WEB.toString(),
                "1234", "new item", 1, "newItemNote", 10, 10,
                "Stand Alone", "READY", "some-url", "some-image",
                cartDataProvider.getItemMetaData(newItemMetaData, new UserItemMetaDataTO()), null,
                null, null)
        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def cartContentResponse = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse])
        def multiCartItemDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(listId, [cartItemResponse.cartItemId], null)
        def recordMetadata = GroovyMock(RecordMetadata)

        deduplicationManager = new DeduplicationManager(cartManager, updateCartItemsManager, deleteCartItemsManager,
                false, 5, 5, true) // turning on rolling update
        when:
        def actual = deduplicationManager.updateDuplicateItems(listId, listId, locationId, newItemsMap, LIST_ITEM_STATE.PENDING).block()

        then:
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(cartContentResponse)
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartItemIds.size() == 1
            assert request.cartItemIds.first() ==  cartItemResponse.cartItemId
            Mono.just(multiCartItemDeleteResponse)
        } // deleting stale items since rolling update is turned on
        1 * eventPublisher.publishEvent(_,_,_) >> Mono.just(recordMetadata)

        actual.first.size() == 1
        actual.second.isEmpty()
        actual.third.isEmpty()
    }

    def "Test updateDuplicateItems() exceeding max pending items with rolling update turned on and not exceeding max count"() {
        given:
        def listItemRequest1 = new ListItemRequestTO(ItemType.TCIN, "tcn1234",  TestListChannel.WEB.toString(), "1234", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest2 = new ListItemRequestTO(ItemType.TCIN, "tcn2345",  TestListChannel.WEB.toString(), "2345", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest3 = new ListItemRequestTO(ItemType.TCIN, "tcn3456",  TestListChannel.WEB.toString(), "3456", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest4 = new ListItemRequestTO(ItemType.TCIN, "tcn4567",  TestListChannel.WEB.toString(), "4567", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest5 = new ListItemRequestTO(ItemType.TCIN, "tcn2222",  TestListChannel.WEB.toString(), "2222", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)

        Map<String, ListItemRequestTO> newItemsMap = [ "tcn1234" : listItemRequest1, "tcn2345" : listItemRequest2,
                                                       "tcn3456" : listItemRequest3, "tcn4567" : listItemRequest4,
                                                       "tcn2222" : listItemRequest5] as LinkedHashMap
        def listId = UUID.randomUUID()

        ListItemMetaDataTO newItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), "tcn1111", TestListChannel.WEB.toString(),
                "1111", "new item", 1, "newItemNote", 10, 10,
                "Stand Alone", "READY", "some-url", "some-image",
                cartDataProvider.getItemMetaData(newItemMetaData, new UserItemMetaDataTO()), null,
                null, null)
        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def cartContentResponse = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse])
        def multiCartItemDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(listId, [cartItemResponse.cartItemId], null)
        def recordMetadata = GroovyMock(RecordMetadata)

        deduplicationManager = new DeduplicationManager(cartManager, updateCartItemsManager, deleteCartItemsManager,
                true, 10, 5, true) // turning on rolling update
        when:
        def actual = deduplicationManager.updateDuplicateItems(listId, listId, locationId, newItemsMap, LIST_ITEM_STATE.PENDING).block()

        then:
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(cartContentResponse)

        actual.first.size() == 1
        actual.second.isEmpty()
        actual.third.isEmpty()
    }

    def "Test updateDuplicateItems() with rolling update turned on and dedup"() {
        given:
        def listItemRequest1 = new ListItemRequestTO(ItemType.TCIN, "tcn1234",  TestListChannel.WEB.toString(), "1234", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest2 = new ListItemRequestTO(ItemType.TCIN, "tcn2345",  TestListChannel.WEB.toString(), "2345", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest3 = new ListItemRequestTO(ItemType.TCIN, "tcn3456",  TestListChannel.WEB.toString(), "3456", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest4 = new ListItemRequestTO(ItemType.TCIN, "tcn4567",  TestListChannel.WEB.toString(), "4567", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest5 = new ListItemRequestTO(ItemType.TCIN, "tcn1111",  TestListChannel.WEB.toString(), "1111", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)

        Map<String, ListItemRequestTO> newItemsMap = [ "tcn1234" : listItemRequest1, "tcn2345" : listItemRequest2,
                                                       "tcn3456" : listItemRequest3, "tcn4567" : listItemRequest4,
                                                       "tcn2222" : listItemRequest5] as LinkedHashMap
        def listId = UUID.randomUUID()

        ListItemMetaDataTO newItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), "tcn1234", TestListChannel.WEB.toString(),
                "1234", "new item", 1, "newItemNote", 10, 10,
                "Stand Alone", "READY", "some-url", "some-image",
                cartDataProvider.getItemMetaData(newItemMetaData, new UserItemMetaDataTO()), null,
                null, null)
        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def cartContentResponse = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse])
        def multiCartItemDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(listId, [cartItemResponse.cartItemId], null)
        def recordMetadata = GroovyMock(RecordMetadata)

        def dedupedPendingCartItem = cartDataProvider.getCartItemResponse(listId, cartItemResponse.cartItemId,
                cartItemResponse.tenantReferenceId, TestListChannel.WEB.toString(), cartItemResponse.tcin, cartItemResponse.tenantItemName,
                listItemRequest1.requestedQuantity + cartItemResponse.requestedQuantity, "note1\nitemNote",
                10, 10, "Stand Alone", "READY", "some-url",
                "some-image", cartDataProvider.getItemMetaData(newItemMetaData, new UserItemMetaDataTO()),
                null, null, null)

        def updateMultiCartItemsResponse = cartDataProvider.getCartContentsResponse(cartResponse, [dedupedPendingCartItem])

        deduplicationManager = new DeduplicationManager(cartManager, updateCartItemsManager, deleteCartItemsManager,
                true, 5, 5, true) // turning on rolling update
        when:
        def actual = deduplicationManager.updateDuplicateItems(listId, listId, locationId, newItemsMap, LIST_ITEM_STATE.PENDING).block()

        then:
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(cartContentResponse)
        1 * cartManager.updateMultiCartItems(_ as UpdateMultiCartItemsRequest) >> { arguments ->
            final UpdateMultiCartItemsRequest updateMultiCartItemsRequest = arguments[0]
            assert updateMultiCartItemsRequest.cartId == listId
            assert updateMultiCartItemsRequest.cartItems.size() == 1
            assert updateMultiCartItemsRequest.cartItems[0].requestedQuantity == listItemRequest1.requestedQuantity + cartItemResponse.requestedQuantity
            Mono.just(updateMultiCartItemsResponse)
        } // update deduped items
        1 * eventPublisher.publishEvent(_,_,_) >> Mono.just(recordMetadata)

        actual.first.size() == 1
        actual.second.size() == 1
        actual.third.size() == 1
    }

    def "Test updateDuplicateItems() with multiple PreExisting items"() {
        given:
        def listItemRequest1 = new ListItemRequestTO(ItemType.TCIN, "tcn1234",  TestListChannel.WEB.toString(), "1234", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest2 = new ListItemRequestTO(ItemType.TCIN, "tcn2345",  TestListChannel.WEB.toString(), "2345", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest3 = new ListItemRequestTO(ItemType.GENERIC_ITEM, "itm3456",  TestListChannel.WEB.toString(), null, "genericItem1",
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest4 = new ListItemRequestTO(ItemType.GENERIC_ITEM, "itm4567", TestListChannel.WEB.toString(), null,  "genericItem2",
                "itemNote", 1, UnitOfMeasure.EACHES, null)

        Map<String, ListItemRequestTO> newItemsMap = [ "tcn1234" : listItemRequest1, "tcn2345" : listItemRequest2,
                                                       "itm3456" : listItemRequest3, "itm4567" : listItemRequest4] as LinkedHashMap
        def listId = UUID.randomUUID()

        ListItemMetaDataTO newItemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), "tcn1234", TestListChannel.WEB.toString(),
                "1234", "new item", 1, "note1", 10, 10,
                "Stand Alone", "READY", "some-url", "some-image",
                cartDataProvider.getItemMetaData(newItemMetaData1, new UserItemMetaDataTO()), null,
                null, null)
        ListItemMetaDataTO newItemMetaData2 = new ListItemMetaDataTO(ItemType.GENERIC_ITEM, LIST_ITEM_STATE.PENDING)
        def cartItemResponse2 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), "itm3456", TestListChannel.WEB.toString(),
                null, "genericItem1", 1, "note2", 10, 10,
                "Stand Alone", "READY", "some-url", "some-image",
                cartDataProvider.getItemMetaData(newItemMetaData2, new UserItemMetaDataTO()), null,
                null, null)
        def cartItemResponse3 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), "itm3456", TestListChannel.WEB.toString(),
                null, "genericItem1", 1, "note3", 10, 10,
                "Stand Alone", "READY", "some-url", "some-image",
                cartDataProvider.getItemMetaData(newItemMetaData2, new UserItemMetaDataTO()), null,
                null, null)
        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def cartContentResponse = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse1, cartItemResponse2, cartItemResponse3])

        def dedupedPendingCartItem1 = cartDataProvider.getCartItemResponse(listId, cartItemResponse1.cartItemId,
                cartItemResponse1.tenantReferenceId, TestListChannel.WEB.toString(), cartItemResponse1.tcin, cartItemResponse1.tenantItemName,
                listItemRequest1.requestedQuantity + cartItemResponse1.requestedQuantity, "note1\nitemNote",
                10, 10, "Stand Alone", "READY", "some-url",
                "some-image", cartDataProvider.getItemMetaData(newItemMetaData1, new UserItemMetaDataTO()),
                null, null, null)

        def dedupedPendingCartItem2 = cartDataProvider.getCartItemResponse(listId, cartItemResponse2.cartItemId,
                cartItemResponse2.tenantReferenceId, TestListChannel.WEB.toString(), cartItemResponse2.tcin, cartItemResponse2.tenantItemName,
                listItemRequest3.requestedQuantity + cartItemResponse2.requestedQuantity + cartItemResponse3.requestedQuantity,
                "note2\nnote3\nitemNote", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image",
                cartDataProvider.getItemMetaData(newItemMetaData2, new UserItemMetaDataTO()), null,
                null, null)

        def updateMultiCartItemsResponse = cartDataProvider.getCartContentsResponse(cartResponse, [dedupedPendingCartItem1, dedupedPendingCartItem2])
        def multiCartItemDeleteResponse = cartDataProvider.getDeleteMultiCartItemResponse(listId, [cartItemResponse3.cartItemId], null)
        def recordMetadata = GroovyMock(RecordMetadata)

        when:
        def actual = deduplicationManager.updateDuplicateItems(listId, listId, locationId, newItemsMap, LIST_ITEM_STATE.PENDING).block()

        then:
        1 * cartManager.getListCartContents(listId, true) >> Mono.just(cartContentResponse)
        1 * cartManager.updateMultiCartItems(_ as UpdateMultiCartItemsRequest) >> { arguments ->
            final UpdateMultiCartItemsRequest updateMultiCartItemsRequest = arguments[0]
            assert updateMultiCartItemsRequest.cartId == listId
            assert updateMultiCartItemsRequest.cartItems.size() == 2
            assert updateMultiCartItemsRequest.cartItems[0].requestedQuantity == listItemRequest1.requestedQuantity + cartItemResponse1.requestedQuantity
            assert updateMultiCartItemsRequest.cartItems[1].requestedQuantity == listItemRequest3.requestedQuantity + cartItemResponse2.requestedQuantity + cartItemResponse3.requestedQuantity
            Mono.just(updateMultiCartItemsResponse)
        } // update deduped items
        1 * cartManager.deleteMultiCartItems(_ as DeleteMultiCartItemsRequest) >> { arguments ->
            final DeleteMultiCartItemsRequest request = arguments[0]
            assert request.cartItemIds.size() == 1
            Mono.just(multiCartItemDeleteResponse)
        } // delete duplicate items
        3 * eventPublisher.publishEvent(_,_,_) >> Mono.just(recordMetadata)

        actual.first.size() == 3
        actual.second.size() == 2
        actual.third.size() == 2

    }
}
