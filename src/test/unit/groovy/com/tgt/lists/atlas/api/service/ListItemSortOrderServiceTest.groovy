//package com.tgt.lists.atlas.api.service
//
//import com.tgt.lists.atlas.api.domain.ListItemSortOrderManager
//import com.tgt.lists.atlas.api.persistence.ListRepository
//import com.tgt.lists.atlas.api.service.ListItemSortOrderService
//import com.tgt.lists.atlas.api.transport.EditItemSortOrderRequestTO
//import com.tgt.lists.atlas.api.transport.ListItemMetaDataTO
//import com.tgt.lists.atlas.api.util.Direction
//import com.tgt.lists.atlas.api.util.ItemType
//import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
//import com.tgt.lists.atlas.kafka.model.MultiDeleteListItem
//import reactor.core.publisher.Mono
//import spock.lang.Specification
//
//class ListItemSortOrderServiceTest extends Specification {
//
//    ListRepository listRepository
//    ListItemSortOrderManager listItemSortOrderManager
//    ListItemSortOrderService listItemSortOrderService
//    String guestId = "1234"
//
//    def setup() {
//        listRepository = Mock(ListRepository)
//        listItemSortOrderManager = new ListItemSortOrderManager(listRepository)
//        listItemSortOrderService = new ListItemSortOrderService(listItemSortOrderManager)
//    }
//
//    def "Test saveListItemSortOrder() when list item status is pending"() {
//        given:
//        UUID listId = UUID.randomUUID()
//        UUID itemId = UUID.randomUUID()
//        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
//        def list = new com.tgt.lists.atlas.api.domain.model.List(listId, itemId.toString(), null, null)
//
//        when:
//        def actual = listItemSortOrderService.saveListItemSortOrder(listId, itemId, itemMetaData).block()
//
//        then:
//        actual
//
//        1 * listRepository.find(listId) >> Mono.empty()
//        1 * listRepository.save(list) >> Mono.just(list)
//    }
//
//    def "Test saveListItemSortOrder() when list item status is completed"() {
//        given:
//        UUID listId = UUID.randomUUID()
//        UUID itemId = UUID.randomUUID()
//        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
//
//        when:
//        def actual = listItemSortOrderService.saveListItemSortOrder(listId, itemId, itemMetaData).block()
//
//        then:
//        actual
//    }
//
//    def "Test deleteListItemSortOrder() when list item status is pending"() {
//        given:
//        UUID listId = UUID.randomUUID()
//        UUID itemId = UUID.randomUUID()
//        UUID itemId1 = UUID.randomUUID()
//        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
//        def multiDeleteListItem = new MultiDeleteListItem(itemId, null, null, null, itemMetaData, null)
//        def preList = new com.tgt.lists.atlas.api.domain.model.List(listId, itemId1.toString() + "," + itemId.toString(), null, null)
//        def postList = new com.tgt.lists.atlas.api.domain.model.List(listId, itemId1.toString(), null, null)
//
//        when:
//        def actual = listItemSortOrderService.deleteListItemSortOrder(listId, [multiDeleteListItem]).block()
//
//        then:
//        actual
//
//        1 * listRepository.find(listId) >> Mono.just(preList)
//        1 * listRepository.updateByListId(listId, postList.listItemSortOrder) >> Mono.just(1)
//    }
//
//
//    def "Test deleteListItemSortOrder() when list item status is pending and item to be deleted is not found"() {
//        given:
//        UUID listId = UUID.randomUUID()
//        UUID itemId = UUID.randomUUID()
//        UUID itemId1 = UUID.randomUUID()
//        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN,
//            LIST_ITEM_STATE.PENDING)
//        def multiDeleteListItem = new MultiDeleteListItem(itemId, null, null, null, itemMetaData, null)
//        def preList = new com.tgt.lists.atlas.api.domain.model.List(listId, itemId1.toString(), null, null)
//
//        when:
//        def actual = listItemSortOrderService.deleteListItemSortOrder(listId, [multiDeleteListItem]).block()
//
//        then:
//        !actual
//
//        1 * listRepository.find(listId) >> Mono.just(preList)
//    }
//
//    def "Test editListItemSortOrder() happy path"() {
//        given:
//        UUID listId = UUID.randomUUID()
//        UUID primaryItemId = UUID.randomUUID()
//        UUID secondaryItemId = UUID.randomUUID()
//        def editItemSortOrderRequestTO = new EditItemSortOrderRequestTO(listId, primaryItemId, secondaryItemId, Direction.ABOVE)
//        def preList = new com.tgt.lists.atlas.api.domain.model.List(listId, secondaryItemId.toString() + "," + primaryItemId.toString(), null, null)
//        def postList = new com.tgt.lists.atlas.api.domain.model.List(listId, primaryItemId.toString() + "," + secondaryItemId.toString(), null, null)
//
//        when:
//        def actual = listItemSortOrderService.editListItemSortOrder(editItemSortOrderRequestTO).block()
//
//        then:
//        actual
//
//        1 * listRepository.find(listId) >> Mono.just(preList)
//        1 * listRepository.updateByListId(listId, postList.listItemSortOrder) >> Mono.just(1)
//    }
//
//    def "Test editListItemSortOrder() when secondary item id does not exist"() {
//        given:
//        UUID listId = UUID.randomUUID()
//        UUID primaryItemId = UUID.randomUUID()
//        UUID secondaryItemId = UUID.randomUUID()
//        def editItemSortOrderRequestTO = new EditItemSortOrderRequestTO(listId, primaryItemId, secondaryItemId, Direction.ABOVE)
//        def preList = new com.tgt.lists.atlas.api.domain.model.List(listId, primaryItemId.toString(), null, null)
//        def postList = new com.tgt.lists.atlas.api.domain.model.List(listId, primaryItemId.toString() + "," + secondaryItemId.toString(), null, null)
//
//        when:
//        def actual = listItemSortOrderService.editListItemSortOrder(editItemSortOrderRequestTO).block()
//
//        then:
//        actual
//
//        1 * listRepository.find(listId) >> Mono.just(preList)
//        1 * listRepository.updateByListId(listId,postList.listItemSortOrder) >> Mono.just(1)
//    }
//
//    def "Test editListItemSortOrder() when primary item id does not exist"() {
//        given:
//        UUID listId = UUID.randomUUID()
//        UUID primaryItemId = UUID.randomUUID()
//        UUID secondaryItemId = UUID.randomUUID()
//        def editItemSortOrderRequestTO = new EditItemSortOrderRequestTO(listId, primaryItemId, secondaryItemId, Direction.ABOVE)
//        def preList = new com.tgt.lists.atlas.api.domain.model.List(listId, secondaryItemId.toString(), null, null)
//        def postList = new com.tgt.lists.atlas.api.domain.model.List(listId, primaryItemId.toString() + "," + secondaryItemId.toString(), null, null)
//
//        when:
//        def actual = listItemSortOrderService.editListItemSortOrder(editItemSortOrderRequestTO).block()
//
//        then:
//        actual
//
//        1 * listRepository.find(listId) >> Mono.just(preList)
//        1 * listRepository.updateByListId(listId,postList.listItemSortOrder) >> Mono.just(1)
//    }
//
//}
