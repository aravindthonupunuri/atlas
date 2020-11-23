package com.tgt.lists.atlas.api.domain

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.api.domain.model.entity.ListPreferenceEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListPreferenceRepository
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.type.Direction
import com.tgt.lists.atlas.util.ListDataProvider
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

class ListPreferenceSortOrderManagerTest extends Specification {

    ListPreferenceSortOrderManager listPreferenceSortOrderManager
    ListPreferenceRepository listPreferenceRepository
    ListRepository listRepository
    ListDataProvider listDataProvider = new ListDataProvider()
    String guestId = "1234"

    def setup() {
        listPreferenceRepository = Mock(ListPreferenceRepository)
        listRepository = Mock(ListRepository)
        listPreferenceSortOrderManager = new ListPreferenceSortOrderManager(listPreferenceRepository, listRepository)
    }

    def "Test saveNewListOrder() when there is no record for list id"() {
        given:
        def listId = Uuids.timeBased()
        def listItemId = Uuids.timeBased()
        ListPreferenceEntity expected = new ListPreferenceEntity(listId, guestId, listItemId.toString())

        when:
        def actual = listPreferenceSortOrderManager.saveNewListItemOrder(guestId, listId, listItemId).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.empty()
        1 * listPreferenceRepository.saveListPreference(expected) >> Mono.just(expected)
        actual == expected
    }

    def "Test saveNewListOrder() when the list id has record"() {
        given:
        def listId = Uuids.timeBased()
        def listItemId = Uuids.timeBased()
        def preSaveOrder = Uuids.timeBased().toString() + "," + Uuids.timeBased().toString()
        ListPreferenceEntity preSaveList = new ListPreferenceEntity(listId, guestId, preSaveOrder)
        ListPreferenceEntity postSaveList = new ListPreferenceEntity(listId, guestId, listItemId.toString() + "," + preSaveOrder)

        when:
        def actual = listPreferenceSortOrderManager.saveNewListItemOrder(guestId,listId, listItemId).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preSaveList)
        1 * listPreferenceRepository.saveListPreference(postSaveList) >> Mono.just(postSaveList)
        0 * listPreferenceRepository.saveListPreference(_)
        actual == postSaveList
    }

    def "Test saveNewListOrder() errors out when getting the list record"() {
        given:
        def listId = Uuids.timeBased()
        def listItemId = Uuids.timeBased()

        when:
        listPreferenceSortOrderManager.saveNewListItemOrder(guestId, listId, listItemId).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.error(new RuntimeException("Some exception"))
        thrown(RuntimeException)
    }

    def "Test saveNewListOrder() errors out when updating the list record"() {
        given:
        def listId = Uuids.timeBased()
        def listItemId = Uuids.timeBased()
        def preSaveOrder = Uuids.timeBased().toString() + "," + Uuids.timeBased().toString()
        ListPreferenceEntity preSaveList = new ListPreferenceEntity(listId, guestId, preSaveOrder)

        when:
        listPreferenceSortOrderManager.saveNewListItemOrder(guestId, listId, listItemId).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preSaveList)
        1 * listPreferenceRepository.saveListPreference(_) >> Mono.error(new RuntimeException("some exception"))
        thrown(RuntimeException)
    }

    def "Test saveNewListOrder() errors out when saving the list record"() {
        given:
        def listId = Uuids.timeBased()
        def listItemId = Uuids.timeBased()
        ListPreferenceEntity postSaveList = new ListPreferenceEntity(listId, guestId, listItemId.toString())

        when:
        listPreferenceSortOrderManager.saveNewListItemOrder(guestId, listId, listItemId).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.empty()
        1 * listPreferenceRepository.saveListPreference(postSaveList) >> Mono.error(new RuntimeException("some exception"))
        thrown(RuntimeException)
    }

    def "Test updateListItemSortOrder() when moving listId3 to position above listId1"() {
        given:
        def listId = Uuids.timeBased()
        def listItemId1 = Uuids.timeBased()
        def listItemId2 = Uuids.timeBased()
        def listItemId3 = Uuids.timeBased()
        def preSortOrder = [listItemId1,listItemId2,listItemId3].join(",")
        def postSortOrder = [listItemId3,listItemId1,listItemId2].join(",")
        ListPreferenceEntity preSaveList = new ListPreferenceEntity(listId, guestId, preSortOrder)
        ListPreferenceEntity expected = new ListPreferenceEntity(listId, guestId, postSortOrder)

        def listItemExtEntities = listDataProvider.createListItemExtEntities(listId, [listItemId1,listItemId2,listItemId3], guestId)

        when:
        def actual = listPreferenceSortOrderManager.updateListItemSortOrder(guestId, listId,
            listItemId3, listItemId1, Direction.ABOVE).block()

        then:
        actual == expected

        1 * listRepository.findListAndItemsByListIdAndItemState(_,_) >> Flux.fromIterable(listItemExtEntities)
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preSaveList)
        1 * listPreferenceRepository.saveListPreference(expected) >> Mono.just(expected)
    }

    def "Test updateListItemSortOrder() when moving listId1 to position below listId3"() {
        given:
        def listId = Uuids.timeBased()
        def listItemId1 = Uuids.timeBased()
        def listItemId2 = Uuids.timeBased()
        def listItemId3 = Uuids.timeBased()
        def preSortOrder = [listItemId1,listItemId2,listItemId3].join(",")
        def postSortOrder = [listItemId2,listItemId3,listItemId1].join(",")
        ListPreferenceEntity preSaveList = new ListPreferenceEntity(listId, guestId, preSortOrder)
        ListPreferenceEntity expected = new ListPreferenceEntity(listId, guestId, postSortOrder)

        def listItemExtEntities = listDataProvider.createListItemExtEntities(listId, [listItemId1,listItemId2,listItemId3], guestId)

        when:
        def actual = listPreferenceSortOrderManager.updateListItemSortOrder(guestId, listId, listItemId1, listItemId3, Direction.BELOW).block()

        then:
        actual == expected

        1 * listRepository.findListAndItemsByListIdAndItemState(_,_) >> Flux.fromIterable(listItemExtEntities)
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preSaveList)
        1 * listPreferenceRepository.saveListPreference(expected) >> Mono.just(expected)
    }

    def "Test updateListItemSortOrder() when moving listId3 to position above listId2"() {
        given:
        def listId = Uuids.timeBased()
        def listItemId1 = Uuids.timeBased()
        def listItemId2 = Uuids.timeBased()
        def listItemId3 = Uuids.timeBased()
        def preSortOrder = [listItemId1,listItemId2,listItemId3].join(",")
        def postSortOrder = [listItemId1,listItemId3,listItemId2].join(",")
        ListPreferenceEntity preSaveList = new ListPreferenceEntity(listId, guestId, preSortOrder)
        ListPreferenceEntity expected = new ListPreferenceEntity(listId, guestId, postSortOrder)

        def listItemExtEntities = listDataProvider.createListItemExtEntities(listId, [listItemId1,listItemId2,listItemId3], guestId)

        when:
        def actual = listPreferenceSortOrderManager.updateListItemSortOrder(guestId, listId,
            listItemId3, listItemId2, Direction.ABOVE).block()

        then:
        actual == expected

        1 * listRepository.findListAndItemsByListIdAndItemState(_,_) >> Flux.fromIterable(listItemExtEntities)
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preSaveList)
        1 * listPreferenceRepository.saveListPreference(expected) >> Mono.just(expected)
    }

    def "Test updateListItemSortOrder() when moving listId4 to position below listId2"() {
        given:
        def listId = Uuids.timeBased()
        def listItemId1 = Uuids.timeBased()
        def listItemId2 = Uuids.timeBased()
        def listItemId3 = Uuids.timeBased()
        def listItemId4 = Uuids.timeBased()
        def preSortOrder = [listItemId1,listItemId2,listItemId3,listItemId4].join(",")
        def postSortOrder = [listItemId1,listItemId2,listItemId4,listItemId3].join(",")
        ListPreferenceEntity preSaveList = new ListPreferenceEntity(listId, guestId, preSortOrder)
        ListPreferenceEntity expected = new ListPreferenceEntity(listId, guestId, postSortOrder)

        def listItemExtEntities = listDataProvider.createListItemExtEntities(listId, [listItemId1,listItemId2,listItemId3], guestId)

        when:
        def actual = listPreferenceSortOrderManager.updateListItemSortOrder(guestId, listId,
            listItemId4, listItemId2, Direction.BELOW).block()

        then:
        actual == expected

        1 * listRepository.findListAndItemsByListIdAndItemState(_,_) >> Flux.fromIterable(listItemExtEntities)
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preSaveList)
        1 * listPreferenceRepository.saveListPreference(expected) >> Mono.just(expected)
    }

    def "Test updateListItemSortOrder() when moving listId 1 to position of listId 3 where listId 3 not present"() {
        given:
        def listId = Uuids.timeBased()
        def listItemId1 = Uuids.timeBased()
        def listItemId2 = Uuids.timeBased()
        def listItemId3 = Uuids.timeBased()
        def preSortOrder = [listItemId1,listItemId2].join(",")
        def postSortOrder = [listItemId2,listItemId1,listItemId3].join(",")
        ListPreferenceEntity preSaveList = new ListPreferenceEntity(listId, guestId, preSortOrder)
        ListPreferenceEntity expected = new ListPreferenceEntity(listId, guestId, postSortOrder)

        def listItemExtEntities = listDataProvider.createListItemExtEntities(listId, [listItemId1,listItemId2,listItemId3], guestId)

        when:
        def actual = listPreferenceSortOrderManager.updateListItemSortOrder(guestId, listId,
            listItemId1, listItemId3, Direction.ABOVE).block()

        then:
        actual == expected

        1 * listRepository.findListAndItemsByListIdAndItemState(_,_) >> Flux.fromIterable(listItemExtEntities)
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preSaveList)
        1 * listPreferenceRepository.saveListPreference(expected) >> Mono.just(expected)
    }


    def "Test updateListItemSortOrder() when moving listId6 to position above listId0"() {
        given:
        def listId = Uuids.timeBased()
        // 8 listItemIds in natural saved order
        def listItemIds = [Uuids.timeBased(),Uuids.timeBased(),Uuids.timeBased(),Uuids.timeBased(),Uuids.timeBased(),Uuids.timeBased(),Uuids.timeBased(),Uuids.timeBased()]
        // saved sorted order in db
        def preSortOrder = [listItemIds[3], listItemIds[0], listItemIds[2], listItemIds[1]].join(",")

        def postSortOrder = [listItemIds[3], listItemIds[6], listItemIds[0], listItemIds[2], listItemIds[1],listItemIds[4], listItemIds[5], listItemIds[7]].join(",")
        ListPreferenceEntity preSaveList = new ListPreferenceEntity(listId, guestId, preSortOrder)
        ListPreferenceEntity expected = new ListPreferenceEntity(listId, guestId, postSortOrder)

        def listItemExtEntities = listDataProvider.createListItemExtEntities(listId, listItemIds, guestId)

        when:
        def actual = listPreferenceSortOrderManager.updateListItemSortOrder(guestId, listId,
                listItemIds[6], listItemIds[0], Direction.ABOVE).block()

        then:
        actual == expected

        1 * listRepository.findListAndItemsByListIdAndItemState(_,_) >> Flux.fromIterable(listItemExtEntities)
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preSaveList)
        1 * listPreferenceRepository.saveListPreference(expected) >> Mono.just(expected)
    }

    def "Test updateListItemSortOrder() when moving listId6 to position above listId0 without existing db sortorder"() {
        given:
        def listId = Uuids.timeBased()
        // 8 listItemIds in natural saved order
        def listItemIds = [Uuids.timeBased(),Uuids.timeBased(),Uuids.timeBased(),Uuids.timeBased(),Uuids.timeBased(),Uuids.timeBased(),Uuids.timeBased(),Uuids.timeBased()]

        def postSortOrder = [listItemIds[6], listItemIds[0], listItemIds[1], listItemIds[2], listItemIds[3],listItemIds[4], listItemIds[5], listItemIds[7]].join(",")

        ListPreferenceEntity expected = new ListPreferenceEntity(listId, guestId, postSortOrder)

        def listItemExtEntities = listDataProvider.createListItemExtEntities(listId, listItemIds, guestId)

        when:
        def actual = listPreferenceSortOrderManager.updateListItemSortOrder(guestId, listId,
                listItemIds[6], listItemIds[0], Direction.ABOVE).block()

        then:
        actual == expected

        1 * listRepository.findListAndItemsByListIdAndItemState(_,_) >> Flux.fromIterable(listItemExtEntities)
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.empty()
        1 * listPreferenceRepository.saveListPreference(expected) >> Mono.just(expected)
    }

    def "Test updateListItemSortOrder() when moving listId6 to position above listId0 with db sortorder access error"() {
        given:
        def listId = Uuids.timeBased()
        // 8 listItemIds in natural saved order
        def listItemIds = [Uuids.timeBased(),Uuids.timeBased(),Uuids.timeBased(),Uuids.timeBased(),Uuids.timeBased(),Uuids.timeBased(),Uuids.timeBased(),Uuids.timeBased()]

        def listItemExtEntities = listDataProvider.createListItemExtEntities(listId, listItemIds, guestId)

        when:
        listPreferenceSortOrderManager.updateListItemSortOrder(guestId, listId,
                listItemIds[6], listItemIds[0], Direction.ABOVE).block()

        then:
        def ex = thrown(RuntimeException)
        ex.message == "access failed"

        1 * listRepository.findListAndItemsByListIdAndItemState(_,_) >> Flux.fromIterable(listItemExtEntities)
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.error(new RuntimeException("access failed"))
        0 * listPreferenceRepository.saveListPreference(_)
    }

    def "Test removeListItemIdFromSortOrder() when removing listId2 from sort order"() {
        given:
        def listId = Uuids.timeBased()
        def listItemId1 = Uuids.timeBased()
        def listItemId2 = Uuids.timeBased()
        def listItemId3 = Uuids.timeBased()
        def preSortOrder = [listItemId1,listItemId2,listItemId3].join(",")
        def postSortOrder = [listItemId1,listItemId3].join(",")
        ListPreferenceEntity preSaveList = new ListPreferenceEntity(listId, guestId, preSortOrder)
        ListPreferenceEntity postSaveList = new ListPreferenceEntity(listId, guestId, postSortOrder)

        when:
        def actual = listPreferenceSortOrderManager.removeListItemIdFromSortOrder(guestId, listId, listItemId2).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preSaveList)
        1 * listPreferenceRepository.saveListPreference(postSaveList) >> Mono.just(postSaveList)
        actual == postSaveList
    }

    def "Test removeListIdFromSortOrder() when removing listId1 from sort order"() {
        given:
        def listId = Uuids.timeBased()
        def listItemId1 = Uuids.timeBased()
        def listItemId2 = Uuids.timeBased()
        def listItemId3 = Uuids.timeBased()
        def preSortOrder = [listItemId1,listItemId2,listItemId3].join(",")
        def postSortOrder = [listItemId2,listItemId3].join(",")
        ListPreferenceEntity preSaveList = new ListPreferenceEntity(listId, guestId, preSortOrder)
        ListPreferenceEntity postSaveList = new ListPreferenceEntity(listId, guestId, postSortOrder)

        when:
        def actual = listPreferenceSortOrderManager.removeListItemIdFromSortOrder(guestId, listId, listItemId1).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preSaveList)
        1 * listPreferenceRepository.saveListPreference(postSaveList) >> Mono.just(postSaveList)
        actual == postSaveList
    }

    def "Test removeListIdFromSortOrder() when removing listId3 from sort order"() {
        given:
        def listId = Uuids.timeBased()
        def listItemId1 = Uuids.timeBased()
        def listItemId2 = Uuids.timeBased()
        def listItemId3 = Uuids.timeBased()
        def preSortOrder = [listItemId1,listItemId2,listItemId3].join(",")
        def postSortOrder = [listItemId1,listItemId2].join(",")
        ListPreferenceEntity preSaveList = new ListPreferenceEntity(listId, guestId, preSortOrder)
        ListPreferenceEntity postSaveList = new ListPreferenceEntity(listId, guestId, postSortOrder)

        when:
        def actual = listPreferenceSortOrderManager.removeListItemIdFromSortOrder(guestId, listId, listItemId3).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preSaveList)
        1 * listPreferenceRepository.saveListPreference(postSaveList) >> Mono.just(postSaveList)
        actual == postSaveList
    }

    def "Test removeListIdFromSortOrder() when removing listId1 and listId3 from sort order"() {
        given:
        def listId = Uuids.timeBased()
        def listItemId1 = Uuids.timeBased()
        def listItemId2 = Uuids.timeBased()
        def listItemId3 = Uuids.timeBased()
        def preSortOrder = [listItemId1,listItemId2,listItemId3].join(",")
        def postSortOrder = listItemId2.toString()
        ListPreferenceEntity preSaveList = new ListPreferenceEntity(listId, guestId, preSortOrder)
        ListPreferenceEntity postSaveList = new ListPreferenceEntity(listId, guestId, postSortOrder)

        when:
        def actual = listPreferenceSortOrderManager.removeListItemIdFromSortOrder(guestId, listId,
            [listItemId1, listItemId3].toArray(new UUID[2])).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preSaveList)
        1 * listPreferenceRepository.saveListPreference(postSaveList) >> Mono.just(postSaveList)
        actual == postSaveList
    }

    def "Test removeListIdFromSortOrder() when removing listId makes the sort order empty"() {
        given:
        def listId = Uuids.timeBased()
        def listItemId1 = Uuids.timeBased()
        def preSortOrder = listItemId1.toString()
        def postSortOrder = ""
        ListPreferenceEntity preSaveList = new ListPreferenceEntity(listId, guestId, preSortOrder)
        ListPreferenceEntity postSaveList = new ListPreferenceEntity(listId, guestId, postSortOrder)

        when:
        def actual = listPreferenceSortOrderManager.removeListItemIdFromSortOrder(guestId, listId, listItemId1).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preSaveList)
        1 * listPreferenceRepository.saveListPreference(postSaveList) >> Mono.just(postSaveList)
        actual == postSaveList
    }

    def "Test removeListIdFromSortOrder() when there is no record for the guest"() {
        given:
        def listId = Uuids.timeBased()
        def listItemId1 = Uuids.timeBased()
        def expected = new ListPreferenceEntity(listId, guestId, "")
        when:
        def actual = listPreferenceSortOrderManager.removeListItemIdFromSortOrder(guestId, listId, listItemId1).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.empty()
        actual == expected
    }

    def "Test removeListIdFromSortOrder() errors when getting the list"() {
        given:
        def listId = Uuids.timeBased()
        def listItemId1 = Uuids.timeBased()

        when:
        listPreferenceSortOrderManager.removeListItemIdFromSortOrder(guestId, listId, listItemId1).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.error(new RuntimeException("some exception"))
        thrown(RuntimeException)
    }

    def "Test getList() when finding list gives empty"() {
        given:
        def listId = Uuids.timeBased()
        def expected = new ListPreferenceEntity(listId, guestId, "")

        when:
        def actual = listPreferenceSortOrderManager.getListPreference(guestId, listId).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.empty()
        actual == expected
    }

    def "Test getList() when finding list errors out"() {
        given:
        def listId = Uuids.timeBased()
        def expected = new ListPreferenceEntity(listId, guestId, "")

        when:
        def actual = listPreferenceSortOrderManager.getListPreference(guestId, listId).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.error(new RuntimeException("some exception"))
        actual == expected
    }
}
