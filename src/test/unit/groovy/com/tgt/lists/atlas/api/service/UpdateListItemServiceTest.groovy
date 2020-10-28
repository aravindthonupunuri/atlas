//package com.tgt.lists.atlas.api.service
//
//import com.datastax.oss.driver.api.core.uuid.Uuids
//import com.fasterxml.jackson.databind.ObjectMapper
//import com.tgt.lists.atlas.api.domain.EventPublisher
//import com.tgt.lists.atlas.api.domain.UpdateListItemManager
//import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
//import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
//import com.tgt.lists.atlas.api.transport.ListItemUpdateRequestTO
//import com.tgt.lists.atlas.api.util.ItemType
//import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
//import com.tgt.lists.atlas.kafka.model.UpdateListItemNotifyEvent
//import com.tgt.lists.atlas.util.CartDataProvider
//import com.tgt.lists.atlas.util.ListDataProvider
//import org.apache.kafka.clients.producer.RecordMetadata
//import reactor.core.publisher.Mono
//import spock.lang.Specification
//
//class UpdateListItemServiceTest extends Specification {
//
//    UpdateListItemService updateListItemService
//    UpdateListItemManager updateListItemManager
//    EventPublisher eventPublisher
//    CartDataProvider cartDataProvider
//    ListDataProvider listDataProvider
//    ListRepository listRepository
//    ObjectMapper objectMapper
//    String guestId = "1234"
//    Long locationId = 1375L
//
//    def setup() {
//        eventPublisher = Mock(EventPublisher)
//        listRepository = Mock(ListRepository)
//        updateListItemManager = new UpdateListItemManager(listRepository, eventPublisher)
//        updateListItemService = new UpdateListItemService(listRepository, updateListItemManager)
//        cartDataProvider = new CartDataProvider()
//        listDataProvider = new ListDataProvider()
//        objectMapper = new ObjectMapper()
//    }
//
//    def "test updateListItem() integrity"() {
//        given:
//        def listItemUpdateRequest = new ListItemUpdateRequestTO(null, null,"updated item note", null, null, null, null, null, null, null)
//        def listId = Uuids.timeBased()
//        def itemId = Uuids.timeBased()
//        def tcin1 = "1234"
//        def tenantRefId1 = cartDataProvider.getItemRefId(ItemType.TCIN, tcin1)
//
//        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.PENDING.name(), ItemType.TCIN.name(), tenantRefId1, tcin1, "title", 1, "note")
//        ListItemEntity updatesListItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.PENDING.name(), ItemType.TCIN.name(), tenantRefId1, tcin1, listItemUpdateRequest.itemTitle, listItemUpdateRequest.requestedQuantity, listItemUpdateRequest.itemNote)
//
//
//        def recordMetadata = GroovyMock(RecordMetadata)
//
//        when:
//        def actual = updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()
//
//        then:
//        1 * listRepository.findListItemByItemId(listId, LIST_ITEM_STATE.PENDING.name(), itemId) >> Mono.just(listItemEntity)
//        // updating duplicate items
//        1 * listRepository.updateListItem(_ as ListItemEntity, _) >> { arguments ->
//            final ListItemEntity updatedlistItem = arguments[0]
//            assert updatedlistItem.id == listId
//            assert updatedlistItem.itemReqQty == listItemUpdateRequest.requestedQuantity
//            Mono.just(updatesListItemEntity)
//        }
//
//        1 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _, _) >> Mono.just(recordMetadata)
//
//        actual.listItemId == updatesListItemEntity.itemId
//        actual.tcin == updatesListItemEntity.itemTcin
//        actual.itemTitle == updatesListItemEntity.itemTitle
//        actual.itemNote == updatesListItemEntity.itemNotes
//        actual.itemType.name() == updatesListItemEntity.itemType
//    }
//
//
//    def "test updateListItem() completed list item integrity"() {
//        given:
//        def listItemUpdateRequest = new ListItemUpdateRequestTO(null, "updated item note", null,
//                null,3, null)
//        def listId = Uuids.timeBased()
//        def itemId = Uuids.timeBased()
//        def tcin1 = "1234"
//        def tenantRefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
//
//        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.COMPLETED.name(), ItemType.TCIN.name(), tenantRefId1, tcin1, "title", 1, "note")
//        ListItemEntity updatesListItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.COMPLETED.name(), ItemType.TCIN.name(), tenantRefId1, tcin1, listItemUpdateRequest.itemTitle, listItemUpdateRequest.requestedQuantity, listItemUpdateRequest.itemNote)
//
//        def recordMetadata = GroovyMock(RecordMetadata)
//
//        when:
//        def actual = updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()
//
//        then:
//        1 * listRepository.findListItemByItemId(listId, LIST_ITEM_STATE.PENDING.name(), itemId) >> Mono.empty()
//        1 * listRepository.findListItemByItemId(listId, LIST_ITEM_STATE.COMPLETED.name(), itemId) >> Mono.just(listItemEntity)
//        // updating duplicate items
//        1 * listRepository.updateListItem(_ as ListItemEntity, _) >> { arguments ->
//            final ListItemEntity updatedlistItem = arguments[0]
//            assert updatedlistItem.id == listId
//            assert updatedlistItem.itemReqQty == listItemUpdateRequest.requestedQuantity
//            Mono.just(updatesListItemEntity)
//        }
//        1 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _, _) >>  Mono.just(recordMetadata)
//
//        actual.listItemId == updatesListItemEntity.itemId
//        actual.tcin == updatesListItemEntity.itemTcin
//        actual.itemTitle == updatesListItemEntity.itemTitle
//        actual.itemNote == updatesListItemEntity.itemNotes
//        actual.itemType.name() == updatesListItemEntity.itemType
//    }
//
//    def "test updateListItem() item not found"() {
//        given:
//        def listItemUpdateRequest = new ListItemUpdateRequestTO(null, "updated item note", null,
//                null,3, null)
//        def listId = Uuids.timeBased()
//        def itemId = Uuids.timeBased()
//
//        when:
//        def actual = updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()
//
//        then:
//        1 * listRepository.findListItemByItemId(listId, LIST_ITEM_STATE.PENDING.name(), itemId) >> Mono.empty()
//        1 * listRepository.findListItemByItemId(listId, LIST_ITEM_STATE.COMPLETED.name(), itemId) >> Mono.empty()
//
//        actual == null
//    }
//
//    def "test updateListItem() integrity with userItemMetaDataTransformationStep"() {
//        given:
//
//        def registryItemMetaData = [
//                "registry-metadata": [
//                        "sub_channel" : "KIOSK"
//                ]
//        ]
//        def userItemMetaData = [
//                "user_meta_data" : [
//                        registryItemMetaData
//                ]
//        ]
//
//        def userItemMetaDataTransformationStep = new DefaultUserItemMetaDataTransformation()
//
//        def listItemUpdateRequest = new ListItemUpdateRequestTO(null, "updated item note", userItemMetaData,
//                null,3, userItemMetaDataTransformationStep)
//        def listId = Uuids.timeBased()
//        def itemId = Uuids.timeBased()
//        def tcin1 = "1234"
//        def tenantRefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
//
//        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.PENDING.name(), ItemType.TCIN.name(), tenantRefId1, tcin1, "title", 1, "note", null)
//        ListItemEntity updatedListItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.COMPLETED.name(), ItemType.TCIN.name(), tenantRefId1, tcin1, listItemUpdateRequest.itemTitle, listItemUpdateRequest.requestedQuantity, listItemUpdateRequest.itemNote, objectMapper.writeValueAsString(listItemUpdateRequest.metadata))
//
//        def recordMetadata = GroovyMock(RecordMetadata)
//
//        when:
//        def actual = updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()
//
//        then:
//
//        1 * listRepository.findListItemByItemId(listId, LIST_ITEM_STATE.PENDING.name(), itemId) >> Mono.just(listItemEntity)
//        // updating duplicate items
//        1 * listRepository.updateListItem(_ as ListItemEntity, _) >> { arguments ->
//            final ListItemEntity updatedListItem = arguments[0]
//            assert updatedListItem.id == listId
//            assert updatedListItem.itemReqQty == listItemUpdateRequest.requestedQuantity
//            Mono.just(updatedListItemEntity)
//        }
//        1 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _, _) >>  Mono.just(recordMetadata)
//
//        actual.listItemId == updatesListItemEntity.itemId
//        actual.tcin == updatesListItemEntity.itemTcin
//        actual.itemTitle == updatesListItemEntity.itemTitle
//        actual.itemNote == updatesListItemEntity.itemNotes
//        actual.itemType.name() == updatesListItemEntity.itemType
//        actual.metadata == listItemUpdateRequest.metadata
//    }
//
//    def "test updateListItem() integrity with empty item title"() {
//        given:
//        def listItemUpdateRequest = new ListItemUpdateRequestTO("  ", "updated item note", null, null,3, null)
//        def listId = Uuids.timeBased()
//        def itemId = Uuids.timeBased()
//
//        when:
//        updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()
//
//        then:
//        thrown(BadRequestException)
//    }
//
//    def "test updateListItem() integrity with empty item title and item state change"() {
//        given:
//        def listItemUpdateRequest = new ListItemUpdateRequestTO("  ", "updated item note", null, LIST_ITEM_STATE.PENDING,3, null)
//        def listId = Uuids.timeBased()
//        def itemId = Uuids.timeBased()
//
//        when:
//        updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()
//
//        then:
//        thrown(BadRequestException)
//    }
//
//    def "test updateListItem() for tcin_item passing item title"() {
//        given:
//        def listItemUpdateRequest = new ListItemUpdateRequestTO("item_title", "updated item note", null, null,3, null)
//        def listId = Uuids.timeBased()
//        def itemId = Uuids.timeBased()
//        def tcin1 = "1234"
//        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
//
//        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
//        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, itemId, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
//                "title", 1, "note",  10, 10, "Stand Alone", "READY",
//                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()), null, null, null)
//        when:
//        updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()
//
//        then:
//        thrown(BadRequestException)
//
//        1 * cartManager.getCartItem(_ ,_) >> Mono.just(cartItemResponse)
//    }
//
//    def "test updateListItem() with updating only the item state from pending to completed"() {
//        given:
//        def listItemUpdateRequest = new ListItemUpdateRequestTO(null, null, null, LIST_ITEM_STATE.COMPLETED,null, null)
//        def listId = Uuids.timeBased()
//        def itemId = Uuids.timeBased()
//        def tcin1 = "1234"
//        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
//        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
//        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, itemId, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
//                "title", 1, "note",  10, 10, "Stand Alone", "READY",
//                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()), null, null, null)
//        def recordMetadata = GroovyMock(RecordMetadata)
//
//        when:
//        def actual = updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()
//
//        then:
//        1 * cartManager.getCartItem(_ ,_ ) >> Mono.just(cartItemResponse)
//        1 * eventPublisher.publishEvent(CompletionItemActionEvent.getEventType(), _, listId.toString()) >>  Mono.just(recordMetadata)
//
//        actual.listItemId == cartItemResponse.cartItemId
//        actual.tcin == cartItemResponse.tcin
//        actual.itemTitle == cartItemResponse.shortDescription
//        actual.itemNote == cartItemResponse.notes
//        actual.price == cartItemResponse.price
//        actual.listPrice == cartItemResponse.listPrice
//        actual.images == cartItemResponse.images
//        actual.itemState == listItemUpdateRequest.itemState
//    }
//
//    def "test updateListItem() with updating only the item state change and error getting cart item"() {
//        given:
//        def listItemUpdateRequest = new ListItemUpdateRequestTO(null, null, null, LIST_ITEM_STATE.COMPLETED,null, null)
//        def listId = Uuids.timeBased()
//        def itemId = Uuids.timeBased()
//
//        when:
//        updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()
//
//        then:
//        1 * cartManager.getCartItem(_ ,_) >> Mono.error(new RuntimeException())
//
//        thrown(RuntimeException)
//    }
//
//    def "test updateListItem() with updating item state from pending to completed"() {
//        given:
//        def listItemUpdateRequest = new ListItemUpdateRequestTO(null, "updated item note", null, LIST_ITEM_STATE.COMPLETED,1, null)
//        def listId = Uuids.timeBased()
//        def itemId = Uuids.timeBased()
//        def tcin1 = "1234"
//        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
//
//        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
//        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, itemId, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
//            "title", 1, "note",  10, 10, "Stand Alone", "READY",
//            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()), null, null, null)
//
//        ListItemMetaDataTO updatedItemMetaData = new ListItemMetaDataTO(ItemType.TCIN,
//            LIST_ITEM_STATE.PENDING)
//        def updatedCartItemResponse = cartDataProvider.getCartItemResponse(listId, itemId, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
//            listItemUpdateRequest.itemTitle, 1, listItemUpdateRequest.itemNote,  10, 10, "Stand Alone", "READY",
//            "some-url", "some-image", cartDataProvider.getItemMetaData(updatedItemMetaData, new UserItemMetaDataTO()), null, null, null)
//
//        def recordMetadata = GroovyMock(RecordMetadata)
//        def metaData = cartDataProvider.getListItemMetaDataFromCart(updatedCartItemResponse.metadata)
//
//        when:
//        def actual = updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()
//
//        then:
//        1 * cartManager.getCartItem(_ ,_) >> Mono.just(cartItemResponse)
//        1 * cartManager.updateCartItem(itemId, _) >> { arguments ->
//            final CartItemUpdateRequest request = arguments[1]
//            def listItemMetaData = cartDataProvider.getListItemMetaDataFromCart(request.metadata as Map)
//            assert listItemMetaData.getItemState() == LIST_ITEM_STATE.PENDING // Item state not changed to completed, its done in the asyc part of the functionality
//            Mono.just(updatedCartItemResponse)
//        }
//        1 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _, _) >>  Mono.just(recordMetadata)
//        1 * eventPublisher.publishEvent(CompletionItemActionEvent.getEventType(), _, listId.toString()) >>  Mono.just(recordMetadata)
//
//        actual.listItemId == updatedCartItemResponse.cartItemId
//        actual.tcin == updatedCartItemResponse.tcin
//        actual.itemTitle == updatedCartItemResponse.shortDescription
//        actual.itemNote == updatedCartItemResponse.notes
//        actual.price == updatedCartItemResponse.price
//        actual.listPrice == updatedCartItemResponse.listPrice
//        actual.images == updatedCartItemResponse.images
//        actual.itemType == metaData.itemType
//        actual.itemState == LIST_ITEM_STATE.COMPLETED
//    }
//
//    def "test updateListItem() with updating item state from completed to pending"() {
//        given:
//        def listItemUpdateRequest = new ListItemUpdateRequestTO(null, "updated item note", null, LIST_ITEM_STATE.PENDING,1, null)
//        def listId = Uuids.timeBased()
//        def completedListId = Uuids.timeBased()
//        def itemId = Uuids.timeBased()
//        def tcin1 = "1234"
//        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
//
//        def completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, listId.toString(),
//                cartDataProvider.getMetaData(new ListMetaDataTO(false,
//                        LIST_STATUS.COMPLETED), new UserMetaDataTO()))
//
//        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
//        def cartItemResponse = cartDataProvider.getCartItemResponse(completedListId, itemId, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
//                "title", 1, "note",  10, 10, "Stand Alone", "READY",
//                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()), null, null, null)
//
//        ListItemMetaDataTO updatedItemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
//        def updatedCartItemResponse = cartDataProvider.getCartItemResponse(listId, itemId, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
//                listItemUpdateRequest.itemTitle, 1, listItemUpdateRequest.itemNote, 10, 10, "Stand Alone", "READY",
//                "some-url", "some-image", cartDataProvider.getItemMetaData(updatedItemMetaData, new UserItemMetaDataTO()), null, null, null)
//        def recordMetadata = GroovyMock(RecordMetadata)
//        def metaData = cartDataProvider.getListItemMetaDataFromCart(updatedCartItemResponse.metadata)
//
//        when:
//        def actual = updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()
//
//        then:
//        1 * cartManager.getCartItem(_, _) >> Mono.empty()
//        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse)
//        1 * cartManager.getCartItem(completedListId, itemId) >> Mono.just(cartItemResponse)
//        1 * cartManager.updateCartItem(itemId, _) >> { arguments ->
//            final CartItemUpdateRequest request = arguments[1]
//            def listItemMetaData = cartDataProvider.getListItemMetaDataFromCart(request.metadata as Map)
//            assert listItemMetaData.getItemState() == LIST_ITEM_STATE.COMPLETED // Item state not changed to pending, its done in the async part of the functionality
//            Mono.just(updatedCartItemResponse)
//        }
//        1 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _, _) >>  Mono.just(recordMetadata)
//        1 * eventPublisher.publishEvent(PendingItemActionEvent.getEventType(), _, listId.toString()) >>  Mono.just(recordMetadata)
//
//        actual.listItemId == updatedCartItemResponse.cartItemId
//        actual.tcin == updatedCartItemResponse.tcin
//        actual.itemTitle == updatedCartItemResponse.shortDescription
//        actual.itemNote == updatedCartItemResponse.notes
//        actual.price == updatedCartItemResponse.price
//        actual.listPrice == updatedCartItemResponse.listPrice
//        actual.images == updatedCartItemResponse.images
//        actual.itemType == metaData.itemType
//        actual.itemState == LIST_ITEM_STATE.PENDING
//
//    }
//
//    def "test updateListItem() with empty request object"() {
//        given:
//        def listItemUpdateRequest = new ListItemUpdateRequestTO("                   ", "", null, null,null, null)
//        def listId = Uuids.timeBased()
//        def itemId = Uuids.timeBased()
//
//        when:
//        updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()
//
//        then:
//        thrown(BadRequestException)
//    }
//
//    def "test updateListItem() with error getting item"() {
//        given:
//        def listItemUpdateRequest = new ListItemUpdateRequestTO("updated item title", "updated item note", null, LIST_ITEM_STATE.PENDING,1, null)
//        def listId = Uuids.timeBased()
//        def itemId = Uuids.timeBased()
//
//        when:
//        updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()
//
//        then:
//        1 * cartManager.getCartItem(_ ,_) >>  Mono.error(new RuntimeException())
//
//        thrown(RuntimeException)
//    }
//
//    def "test updateListItem() when failure to publish completion item action event into message bus topic and DLQ topic"() {
//        given:
//        def listItemUpdateRequest = new ListItemUpdateRequestTO(null, "updated item note", null, LIST_ITEM_STATE.COMPLETED,1, null)
//        def listId = Uuids.timeBased()
//        def itemId = Uuids.timeBased()
//        def tcin1 = "1234"
//        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
//
//        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
//        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, itemId, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
//                "title", 1, "note",  10, 10, "Stand Alone", "READY",
//                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()), null, null, null)
//
//        ListItemMetaDataTO updatedItemMetaData = new ListItemMetaDataTO(ItemType.TCIN,
//                LIST_ITEM_STATE.PENDING)
//        def updatedCartItemResponse = cartDataProvider.getCartItemResponse(listId, itemId, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
//                listItemUpdateRequest.itemTitle, 1, listItemUpdateRequest.itemNote,  10, 10, "Stand Alone", "READY",
//                "some-url", "some-image", cartDataProvider.getItemMetaData(updatedItemMetaData, new UserItemMetaDataTO()), null, null, null)
//        def recordMetadata = GroovyMock(RecordMetadata)
//        def metaData = cartDataProvider.getListItemMetaDataFromCart(updatedCartItemResponse.metadata)
//
//        when:
//        def actual = updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()
//
//        then:
//        1 * cartManager.getCartItem(_ ,_) >> Mono.just(cartItemResponse)
//        1 * cartManager.updateCartItem(itemId, _) >> { arguments ->
//            final CartItemUpdateRequest request = arguments[1]
//            def listItemMetaData = cartDataProvider.getListItemMetaDataFromCart(request.metadata as Map)
//            assert listItemMetaData.getItemState() == LIST_ITEM_STATE.PENDING // Item state not changed to completed, its done in the asyc part of the functionality
//            Mono.just(updatedCartItemResponse)
//        }
//        1 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _, _) >>  Mono.just(recordMetadata)
//        1 * eventPublisher.publishEvent(CompletionItemActionEvent.getEventType(), _, listId.toString()) >>  Mono.error(new RuntimeException())
//
//        actual.listItemId == updatedCartItemResponse.cartItemId
//        actual.tcin == updatedCartItemResponse.tcin
//        actual.itemTitle == updatedCartItemResponse.shortDescription
//        actual.itemNote == updatedCartItemResponse.notes
//        actual.price == updatedCartItemResponse.price
//        actual.listPrice == updatedCartItemResponse.listPrice
//        actual.images == updatedCartItemResponse.images
//        actual.itemType == metaData.itemType
//    }
//
//    def "test onlyItemStateUpdate() integrity "() {
//        given:
//        def listItemUpdateRequest = new ListItemUpdateRequestTO(null, null, null, LIST_ITEM_STATE.COMPLETED,null, null)
//
//        when:
//        def actual = listItemUpdateRequest.onlyItemStateUpdate()
//
//        then:
//        actual
//    }
//
//    class DefaultUserItemMetaDataTransformation implements UserItemMetaDataTransformationStep {
//
//        @Override
//        Mono<UserItemMetaDataTO> execute(@NotNull UserItemMetaDataTO userItemMetaDataTO) {
//            return Mono.just(userItemMetaDataTO)
//        }
//    }
// }