package com.tgt.lists.atlas.api.domain


import com.tgt.lists.atlas.api.persistence.ListRepository
import com.tgt.lists.atlas.api.util.Direction
import reactor.core.publisher.Mono
import spock.lang.Specification

class ListItemSortOrderManagerTest extends Specification {

    ListItemSortOrderManager listItemSortOrderManager
    ListRepository listRepository

    def setup() {
        listRepository = Mock(ListRepository)
        listItemSortOrderManager = new ListItemSortOrderManager(listRepository)
    }

    def "Test saveNewListOrder() when there is no record for list id"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId = UUID.randomUUID()
        com.tgt.lists.atlas.api.domain.model.List expected = new com.tgt.lists.atlas.api.domain.model.List(listId, listItemId.toString(), null, null)

        when:
        def actual = listItemSortOrderManager.saveNewListItemOrder(listId, listItemId).block()

        then:
        1 * listRepository.find(listId) >> Mono.empty()
        1 * listRepository.save(expected) >> Mono.just(expected)
        actual == expected
    }

    def "Test saveNewListOrder() when the list id has record"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId = UUID.randomUUID()
        def preSaveOrder = UUID.randomUUID().toString() + "," + UUID.randomUUID().toString()
        com.tgt.lists.atlas.api.domain.model.List preSaveList = new com.tgt.lists.atlas.api.domain.model.List(listId, preSaveOrder, null, null)
        com.tgt.lists.atlas.api.domain.model.List postSaveList = new com.tgt.lists.atlas.api.domain.model.List(listId, listItemId.toString() + "," + preSaveOrder, null, null)

        when:
        def actual = listItemSortOrderManager.saveNewListItemOrder(listId, listItemId).block()

        then:
        1 * listRepository.find(listId) >> Mono.just(preSaveList)
        1 * listRepository.updateByListId(listId, postSaveList.listItemSortOrder) >> Mono.just(1)
        0 * listRepository.save(_)
        actual == postSaveList
    }

    def "Test saveNewListOrder() errors out when getting the list record"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId = UUID.randomUUID()

        when:
        listItemSortOrderManager.saveNewListItemOrder(listId, listItemId).block()

        then:
        1 * listRepository.find(listId) >> Mono.error(new RuntimeException("Some exception"))
        thrown(RuntimeException)
    }

    def "Test saveNewListOrder() errors out when updating the list record"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId = UUID.randomUUID()
        def preSaveOrder = UUID.randomUUID().toString() + "," + UUID.randomUUID().toString()
        com.tgt.lists.atlas.api.domain.model.List preSaveList = new com.tgt.lists.atlas.api.domain.model.List(listId, preSaveOrder, null, null)
        com.tgt.lists.atlas.api.domain.model.List postSaveList = new com.tgt.lists.atlas.api.domain.model.List(listId, listItemId.toString() + "," + preSaveOrder, null, null)

        when:
        listItemSortOrderManager.saveNewListItemOrder(listId, listItemId).block()

        then:
        1 * listRepository.find(listId) >> Mono.just(preSaveList)
        1 * listRepository.updateByListId(listId, postSaveList.listItemSortOrder) >> Mono.error(new RuntimeException("some exception"))
        thrown(RuntimeException)
    }

    def "Test saveNewListOrder() errors out when saving the list record"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId = UUID.randomUUID()
        com.tgt.lists.atlas.api.domain.model.List postSaveList = new com.tgt.lists.atlas.api.domain.model.List(listId, listItemId.toString(), null, null)

        when:
        listItemSortOrderManager.saveNewListItemOrder(listId, listItemId).block()

        then:
        1 * listRepository.find(listId) >> Mono.empty()
        1 * listRepository.save(postSaveList) >> Mono.error(new RuntimeException("some exception"))
        thrown(RuntimeException)
    }

    def "Test updateListItemSortOrder() when moving listId3 to position above listId1"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId1 = UUID.randomUUID()
        def listItemId2 = UUID.randomUUID()
        def listItemId3 = UUID.randomUUID()
        def preSortOrder = listItemId1.toString() + "," + listItemId2.toString() + "," + listItemId3.toString()
        def postSortOrder = listItemId3.toString() + "," + listItemId1.toString() + "," + listItemId2.toString()
        com.tgt.lists.atlas.api.domain.model.List preSaveList = new com.tgt.lists.atlas.api.domain.model.List(listId, preSortOrder, null, null)
        com.tgt.lists.atlas.api.domain.model.List postSaveList = new com.tgt.lists.atlas.api.domain.model.List(listId, postSortOrder, null, null)

        when:
        def actual = listItemSortOrderManager.updateListItemSortOrder(listId,
            listItemId3, listItemId1, Direction.ABOVE).block()

        then:
        1 * listRepository.find(listId) >> Mono.just(preSaveList)
        1 * listRepository.updateByListId(listId, postSortOrder) >> Mono.just(1)
        actual == postSaveList
    }

    def "Test updateListItemSortOrder() when moving listId1 to position below listId3"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId1 = UUID.randomUUID()
        def listItemId2 = UUID.randomUUID()
        def listItemId3 = UUID.randomUUID()
        def preSortOrder = listItemId1.toString() + "," + listItemId2.toString() + "," + listItemId3.toString()
        def postSortOrder = listItemId2.toString() + "," + listItemId3.toString() + "," + listItemId1.toString()
        com.tgt.lists.atlas.api.domain.model.List preSaveList = new com.tgt.lists.atlas.api.domain.model.List(listId, preSortOrder, null, null)
        com.tgt.lists.atlas.api.domain.model.List postSaveList = new com.tgt.lists.atlas.api.domain.model.List(listId, postSortOrder, null, null)

        when:
        def actual = listItemSortOrderManager.updateListItemSortOrder(listId,
            listItemId1, listItemId3, Direction.BELOW).block()

        then:
        1 * listRepository.find(listId) >> Mono.just(preSaveList)
        1 * listRepository.updateByListId(listId, postSortOrder) >> Mono.just(1)
        actual == postSaveList
    }

    def "Test updateListItemSortOrder() when moving listId3 to position above listId2"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId1 = UUID.randomUUID()
        def listItemId2 = UUID.randomUUID()
        def listItemId3 = UUID.randomUUID()
        def preSortOrder = listItemId1.toString() + "," + listItemId2.toString() + "," + listItemId3.toString()
        def postSortOrder = listItemId1.toString() + "," + listItemId3.toString() + "," + listItemId2.toString()
        com.tgt.lists.atlas.api.domain.model.List preSaveList = new com.tgt.lists.atlas.api.domain.model.List(listId, preSortOrder, null, null)
        com.tgt.lists.atlas.api.domain.model.List postSaveList = new com.tgt.lists.atlas.api.domain.model.List(listId, postSortOrder, null, null)

        when:
        def actual = listItemSortOrderManager.updateListItemSortOrder(listId,
            listItemId3, listItemId2, Direction.ABOVE).block()

        then:
        1 * listRepository.find(listId) >> Mono.just(preSaveList)
        1 * listRepository.updateByListId(listId, postSortOrder) >> Mono.just(1)
        actual == postSaveList
    }

    def "Test updateListItemSortOrder() when moving listId4 to position below listId2"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId1 = UUID.randomUUID()
        def listItemId2 = UUID.randomUUID()
        def listItemId3 = UUID.randomUUID()
        def listItemId4 = UUID.randomUUID()
        def preSortOrder = listItemId1.toString() + "," + listItemId2.toString() + "," + listItemId3.toString() + "," + listItemId4.toString()
        def postSortOrder = listItemId1.toString() + "," + listItemId2.toString() + "," + listItemId4.toString() + "," + listItemId3.toString()
        com.tgt.lists.atlas.api.domain.model.List preSaveList = new com.tgt.lists.atlas.api.domain.model.List(listId, preSortOrder, null, null)
        com.tgt.lists.atlas.api.domain.model.List postSaveList = new com.tgt.lists.atlas.api.domain.model.List(listId, postSortOrder, null, null)

        when:
        def actual = listItemSortOrderManager.updateListItemSortOrder(listId,
            listItemId4, listItemId2, Direction.BELOW).block()

        then:
        1 * listRepository.find(listId) >> Mono.just(preSaveList)
        1 * listRepository.updateByListId(listId, postSortOrder) >> Mono.just(1)
        actual == postSaveList
    }

    def "Test updateListItemSortOrder() when moving listId 1 to position of listId 3 where listId 3 not present"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId1 = UUID.randomUUID()
        def listItemId2 = UUID.randomUUID()
        def listItemId3 = UUID.randomUUID()
        def preSortOrder = listItemId1.toString() + "," + listItemId2.toString()
        def postSortOrder = listItemId1.toString() + "," + listItemId3.toString() + "," + listItemId2.toString()
        com.tgt.lists.atlas.api.domain.model.List preSaveList = new com.tgt.lists.atlas.api.domain.model.List(listId, preSortOrder, null, null)
        com.tgt.lists.atlas.api.domain.model.List expected = new com.tgt.lists.atlas.api.domain.model.List(listId, postSortOrder, null, null)

        when:
        def actual = listItemSortOrderManager.updateListItemSortOrder(listId,
            listItemId1, listItemId3, Direction.ABOVE).block()

        then:
        actual == expected

        1 * listRepository.find(listId) >> Mono.just(preSaveList)
        1 * listRepository.updateByListId(listId, postSortOrder) >> Mono.just(1)
    }

    def "Test removeListItemIdFromSortOrder() when removing listId2 from sort order"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId1 = UUID.randomUUID()
        def listItemId2 = UUID.randomUUID()
        def listItemId3 = UUID.randomUUID()
        def preSortOrder = listItemId1.toString() + "," + listItemId2.toString() + "," + listItemId3.toString()
        def postSortOrder = listItemId1.toString() + "," + listItemId3.toString()
        com.tgt.lists.atlas.api.domain.model.List preSaveList = new com.tgt.lists.atlas.api.domain.model.List(listId, preSortOrder, null, null)
        com.tgt.lists.atlas.api.domain.model.List postSaveList = new com.tgt.lists.atlas.api.domain.model.List(listId, postSortOrder, null, null)

        when:
        def actual = listItemSortOrderManager.removeListItemIdFromSortOrder(listId, listItemId2).block()

        then:
        1 * listRepository.find(listId) >> Mono.just(preSaveList)
        1 * listRepository.updateByListId(listId, postSortOrder) >> Mono.just(1)
        actual == postSaveList
    }

    def "Test removeListIdFromSortOrder() when removing listId1 from sort order"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId1 = UUID.randomUUID()
        def listItemId2 = UUID.randomUUID()
        def listItemId3 = UUID.randomUUID()
        def preSortOrder = listItemId1.toString() + "," + listItemId2.toString() + "," + listItemId3.toString()
        def postSortOrder = listItemId2.toString() + "," + listItemId3.toString()
        com.tgt.lists.atlas.api.domain.model.List preSaveList = new com.tgt.lists.atlas.api.domain.model.List(listId, preSortOrder, null, null)
        com.tgt.lists.atlas.api.domain.model.List postSaveList = new com.tgt.lists.atlas.api.domain.model.List(listId, postSortOrder, null, null)

        when:
        def actual = listItemSortOrderManager.removeListItemIdFromSortOrder(listId, listItemId1).block()

        then:
        1 * listRepository.find(listId) >> Mono.just(preSaveList)
        1 * listRepository.updateByListId(listId, postSortOrder) >> Mono.just(1)
        actual == postSaveList
    }

    def "Test removeListIdFromSortOrder() when removing listId3 from sort order"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId1 = UUID.randomUUID()
        def listItemId2 = UUID.randomUUID()
        def listItemId3 = UUID.randomUUID()
        def preSortOrder = listItemId1.toString() + "," + listItemId2.toString() + "," + listItemId3.toString()
        def postSortOrder = listItemId1.toString() + "," + listItemId2.toString()
        com.tgt.lists.atlas.api.domain.model.List preSaveList = new com.tgt.lists.atlas.api.domain.model.List(listId, preSortOrder, null, null)
        com.tgt.lists.atlas.api.domain.model.List postSaveList = new com.tgt.lists.atlas.api.domain.model.List(listId, postSortOrder, null, null)

        when:
        def actual = listItemSortOrderManager.removeListItemIdFromSortOrder(listId, listItemId3).block()

        then:
        1 * listRepository.find(listId) >> Mono.just(preSaveList)
        1 * listRepository.updateByListId(listId, postSortOrder) >> Mono.just(1)
        actual == postSaveList
    }

    def "Test removeListIdFromSortOrder() when removing listId1 and listId3 from sort order"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId1 = UUID.randomUUID()
        def listItemId2 = UUID.randomUUID()
        def listItemId3 = UUID.randomUUID()
        def preSortOrder = listItemId1.toString() + "," + listItemId2.toString() + "," + listItemId3.toString()
        def postSortOrder = listItemId2.toString()
        com.tgt.lists.atlas.api.domain.model.List preSaveList = new com.tgt.lists.atlas.api.domain.model.List(listId, preSortOrder, null, null)
        com.tgt.lists.atlas.api.domain.model.List postSaveList = new com.tgt.lists.atlas.api.domain.model.List(listId, postSortOrder, null, null)

        when:
        def actual = listItemSortOrderManager.removeListItemIdFromSortOrder(listId,
            [listItemId1, listItemId3].toArray(new UUID[2])).block()

        then:
        1 * listRepository.find(listId) >> Mono.just(preSaveList)
        1 * listRepository.updateByListId(listId, postSortOrder) >> Mono.just(1)
        actual == postSaveList
    }

    def "Test removeListIdFromSortOrder() when removing listId makes the sort order empty"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId1 = UUID.randomUUID()
        def preSortOrder = listItemId1.toString()
        def postSortOrder = ""
        com.tgt.lists.atlas.api.domain.model.List preSaveList = new com.tgt.lists.atlas.api.domain.model.List(listId, preSortOrder, null, null)
        com.tgt.lists.atlas.api.domain.model.List postSaveList = new com.tgt.lists.atlas.api.domain.model.List(listId, postSortOrder, null, null)

        when:
        def actual = listItemSortOrderManager.removeListItemIdFromSortOrder(listId, listItemId1).block()

        then:
        1 * listRepository.find(listId) >> Mono.just(preSaveList)
        1 * listRepository.updateByListId(listId, postSortOrder) >> Mono.just(1)
        actual == postSaveList
    }

    def "Test removeListIdFromSortOrder() when there is no record for the guest"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId1 = UUID.randomUUID()
        def expected = new com.tgt.lists.atlas.api.domain.model.List(listId, "", null, null)
        when:
        def actual = listItemSortOrderManager.removeListItemIdFromSortOrder(listId, listItemId1).block()

        then:
        1 * listRepository.find(listId) >> Mono.empty()
        actual == expected
    }

    def "Test removeListIdFromSortOrder() errors when getting the list"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId1 = UUID.randomUUID()

        when:
        listItemSortOrderManager.removeListItemIdFromSortOrder(listId, listItemId1).block()

        then:
        1 * listRepository.find(listId) >> Mono.error(new RuntimeException("some exception"))
        thrown(RuntimeException)
    }

    def "Test getList() when finding list gives empty"() {
        given:
        def listId = UUID.randomUUID()
        def expected = new com.tgt.lists.atlas.api.domain.model.List(listId, "", null, null)

        when:
        def actual = listItemSortOrderManager.getList(listId).block()

        then:
        1 * listRepository.find(listId) >> Mono.empty()
        actual == expected
    }

    def "Test getList() when finding list errors out"() {
        given:
        def listId = UUID.randomUUID()
        def expected = new com.tgt.lists.atlas.api.domain.model.List(listId, "", null, null)

        when:
        def actual = listItemSortOrderManager.getList(listId).block()

        then:
        1 * listRepository.find(listId) >> Mono.error(new RuntimeException("some exception"))
        actual == expected
    }
}
