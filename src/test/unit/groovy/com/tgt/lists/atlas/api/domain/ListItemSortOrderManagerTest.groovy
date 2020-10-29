package com.tgt.lists.atlas.api.domain

import com.tgt.lists.atlas.api.domain.model.entity.ListPreferenceEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListPreferenceRepository
import com.tgt.lists.atlas.api.util.Direction
import reactor.core.publisher.Mono
import spock.lang.Specification

class ListItemSortOrderManagerTest extends Specification {

    ListItemSortOrderManager listItemSortOrderManager
    ListPreferenceRepository listPreferenceRepository
    String guestId = "1234"

    def setup() {
        listPreferenceRepository = Mock(ListPreferenceRepository)
        listItemSortOrderManager = new ListItemSortOrderManager(listPreferenceRepository)
    }

    def "Test saveNewListOrder() when there is no record for list id"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId = UUID.randomUUID()
        ListPreferenceEntity expected = new ListPreferenceEntity(listId, guestId, listItemId.toString())

        when:
        def actual = listItemSortOrderManager.saveNewListItemOrder(guestId, listId, listItemId).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.empty()
        1 * listPreferenceRepository.saveListPreference(expected) >> Mono.just(expected)
        actual == expected
    }

    def "Test saveNewListOrder() when the list id has record"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId = UUID.randomUUID()
        def preSaveOrder = UUID.randomUUID().toString() + "," + UUID.randomUUID().toString()
        ListPreferenceEntity preSaveList = new ListPreferenceEntity(listId, guestId, preSaveOrder)
        ListPreferenceEntity postSaveList = new ListPreferenceEntity(listId, guestId, listItemId.toString() + "," + preSaveOrder)

        when:
        def actual = listItemSortOrderManager.saveNewListItemOrder(guestId,listId, listItemId).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preSaveList)
        1 * listPreferenceRepository.saveListPreference(postSaveList) >> Mono.just(postSaveList)
        0 * listPreferenceRepository.saveListPreference(_)
        actual == postSaveList
    }

    def "Test saveNewListOrder() errors out when getting the list record"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId = UUID.randomUUID()

        when:
        listItemSortOrderManager.saveNewListItemOrder(guestId, listId, listItemId).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.error(new RuntimeException("Some exception"))
        thrown(RuntimeException)
    }

    def "Test saveNewListOrder() errors out when updating the list record"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId = UUID.randomUUID()
        def preSaveOrder = UUID.randomUUID().toString() + "," + UUID.randomUUID().toString()
        ListPreferenceEntity preSaveList = new ListPreferenceEntity(listId, guestId, preSaveOrder)

        when:
        listItemSortOrderManager.saveNewListItemOrder(guestId, listId, listItemId).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preSaveList)
        1 * listPreferenceRepository.saveListPreference(_) >> Mono.error(new RuntimeException("some exception"))
        thrown(RuntimeException)
    }

    def "Test saveNewListOrder() errors out when saving the list record"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId = UUID.randomUUID()
        ListPreferenceEntity postSaveList = new ListPreferenceEntity(listId, guestId, listItemId.toString())

        when:
        listItemSortOrderManager.saveNewListItemOrder(guestId, listId, listItemId).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.empty()
        1 * listPreferenceRepository.saveListPreference(postSaveList) >> Mono.error(new RuntimeException("some exception"))
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
        ListPreferenceEntity preSaveList = new ListPreferenceEntity(listId, guestId, preSortOrder)
        ListPreferenceEntity postSaveList = new ListPreferenceEntity(listId, guestId, postSortOrder)

        when:
        def actual = listItemSortOrderManager.updateListItemSortOrder(guestId, listId,
            listItemId3, listItemId1, Direction.ABOVE).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preSaveList)
        1 * listPreferenceRepository.saveListPreference(postSaveList) >> Mono.just(postSaveList)
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
        ListPreferenceEntity preSaveList = new ListPreferenceEntity(listId, guestId, preSortOrder)
        ListPreferenceEntity postSaveList = new ListPreferenceEntity(listId, guestId, postSortOrder)

        when:
        def actual = listItemSortOrderManager.updateListItemSortOrder(guestId, listId, listItemId1, listItemId3, Direction.BELOW).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preSaveList)
        1 * listPreferenceRepository.saveListPreference(postSaveList) >> Mono.just(postSaveList)
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
        ListPreferenceEntity preSaveList = new ListPreferenceEntity(listId, guestId, preSortOrder)
        ListPreferenceEntity postSaveList = new ListPreferenceEntity(listId, guestId, postSortOrder)

        when:
        def actual = listItemSortOrderManager.updateListItemSortOrder(guestId, listId,
            listItemId3, listItemId2, Direction.ABOVE).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preSaveList)
        1 * listPreferenceRepository.saveListPreference(postSaveList) >> Mono.just(postSaveList)
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
        ListPreferenceEntity preSaveList = new ListPreferenceEntity(listId, guestId, preSortOrder)
        ListPreferenceEntity postSaveList = new ListPreferenceEntity(listId, guestId, postSortOrder)

        when:
        def actual = listItemSortOrderManager.updateListItemSortOrder(guestId, listId,
            listItemId4, listItemId2, Direction.BELOW).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preSaveList)
        1 * listPreferenceRepository.saveListPreference(postSaveList) >> Mono.just(postSaveList)
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
        ListPreferenceEntity preSaveList = new ListPreferenceEntity(listId, guestId, preSortOrder)
        ListPreferenceEntity expected = new ListPreferenceEntity(listId, guestId, postSortOrder)

        when:
        def actual = listItemSortOrderManager.updateListItemSortOrder(guestId, listId,
            listItemId1, listItemId3, Direction.ABOVE).block()

        then:
        actual == expected

        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preSaveList)
        1 * listPreferenceRepository.saveListPreference(expected) >> Mono.just(expected)
    }

    def "Test removeListItemIdFromSortOrder() when removing listId2 from sort order"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId1 = UUID.randomUUID()
        def listItemId2 = UUID.randomUUID()
        def listItemId3 = UUID.randomUUID()
        def preSortOrder = listItemId1.toString() + "," + listItemId2.toString() + "," + listItemId3.toString()
        def postSortOrder = listItemId1.toString() + "," + listItemId3.toString()
        ListPreferenceEntity preSaveList = new ListPreferenceEntity(listId, guestId, preSortOrder)
        ListPreferenceEntity postSaveList = new ListPreferenceEntity(listId, guestId, postSortOrder)

        when:
        def actual = listItemSortOrderManager.removeListItemIdFromSortOrder(guestId, listId, listItemId2).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preSaveList)
        1 * listPreferenceRepository.saveListPreference(postSaveList) >> Mono.just(postSaveList)
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
        ListPreferenceEntity preSaveList = new ListPreferenceEntity(listId, guestId, preSortOrder)
        ListPreferenceEntity postSaveList = new ListPreferenceEntity(listId, guestId, postSortOrder)

        when:
        def actual = listItemSortOrderManager.removeListItemIdFromSortOrder(guestId, listId, listItemId1).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preSaveList)
        1 * listPreferenceRepository.saveListPreference(postSaveList) >> Mono.just(postSaveList)
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
        ListPreferenceEntity preSaveList = new ListPreferenceEntity(listId, guestId, preSortOrder)
        ListPreferenceEntity postSaveList = new ListPreferenceEntity(listId, guestId, postSortOrder)

        when:
        def actual = listItemSortOrderManager.removeListItemIdFromSortOrder(guestId, listId, listItemId3).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preSaveList)
        1 * listPreferenceRepository.saveListPreference(postSaveList) >> Mono.just(postSaveList)
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
        ListPreferenceEntity preSaveList = new ListPreferenceEntity(listId, guestId, preSortOrder)
        ListPreferenceEntity postSaveList = new ListPreferenceEntity(listId, guestId, postSortOrder)

        when:
        def actual = listItemSortOrderManager.removeListItemIdFromSortOrder(guestId, listId,
            [listItemId1, listItemId3].toArray(new UUID[2])).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preSaveList)
        1 * listPreferenceRepository.saveListPreference(postSaveList) >> Mono.just(postSaveList)
        actual == postSaveList
    }

    def "Test removeListIdFromSortOrder() when removing listId makes the sort order empty"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId1 = UUID.randomUUID()
        def preSortOrder = listItemId1.toString()
        def postSortOrder = ""
        ListPreferenceEntity preSaveList = new ListPreferenceEntity(listId, guestId, preSortOrder)
        ListPreferenceEntity postSaveList = new ListPreferenceEntity(listId, guestId, postSortOrder)

        when:
        def actual = listItemSortOrderManager.removeListItemIdFromSortOrder(guestId, listId, listItemId1).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preSaveList)
        1 * listPreferenceRepository.saveListPreference(postSaveList) >> Mono.just(postSaveList)
        actual == postSaveList
    }

    def "Test removeListIdFromSortOrder() when there is no record for the guest"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId1 = UUID.randomUUID()
        def expected = new ListPreferenceEntity(listId, guestId, "")
        when:
        def actual = listItemSortOrderManager.removeListItemIdFromSortOrder(guestId, listId, listItemId1).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.empty()
        actual == expected
    }

    def "Test removeListIdFromSortOrder() errors when getting the list"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId1 = UUID.randomUUID()

        when:
        listItemSortOrderManager.removeListItemIdFromSortOrder(guestId, listId, listItemId1).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.error(new RuntimeException("some exception"))
        thrown(RuntimeException)
    }

    def "Test getList() when finding list gives empty"() {
        given:
        def listId = UUID.randomUUID()
        def expected = new ListPreferenceEntity(listId, guestId, "")

        when:
        def actual = listItemSortOrderManager.getList(guestId, listId).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.empty()
        actual == expected
    }

    def "Test getList() when finding list errors out"() {
        given:
        def listId = UUID.randomUUID()
        def expected = new ListPreferenceEntity(listId, guestId, "")

        when:
        def actual = listItemSortOrderManager.getList(guestId, listId).block()

        then:
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.error(new RuntimeException("some exception"))
        actual == expected
    }
}
