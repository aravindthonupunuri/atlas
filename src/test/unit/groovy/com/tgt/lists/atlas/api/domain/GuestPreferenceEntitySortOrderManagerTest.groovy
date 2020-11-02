package com.tgt.lists.atlas.api.domain

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.api.domain.model.entity.GuestPreferenceEntity
import com.tgt.lists.atlas.api.persistence.cassandra.GuestPreferenceRepository
import com.tgt.lists.atlas.api.util.Direction
import reactor.core.publisher.Mono
import spock.lang.Specification

class GuestPreferenceEntitySortOrderManagerTest extends Specification {

    GuestPreferenceSortOrderManager guestListOrderManager
    GuestPreferenceRepository guestListRepository
    String guestId = "1234"

    def setup() {
        guestListRepository = Mock(GuestPreferenceRepository)
        guestListOrderManager = new GuestPreferenceSortOrderManager(guestListRepository)
    }

    def "Test saveNewOrder() when the guest does not have any record"() {
        given:
        def listId = Uuids.timeBased()
        GuestPreferenceEntity expected = new GuestPreferenceEntity(guestId, listId.toString())

        when:
        def actual = guestListOrderManager.saveNewOrder(guestId, listId).block()

        then:
        1 * guestListRepository.findGuestPreference(guestId) >> Mono.empty()
        1 * guestListRepository.saveGuestPreference(expected) >> Mono.just(expected)
        actual == expected
    }

    def "Test saveNewOrder() when the guest has record"() {
        given:
        def listId = Uuids.timeBased()
        def preSortOrder = Uuids.timeBased().toString()
        def postSortOrder = listId.toString() + "," + preSortOrder
        GuestPreferenceEntity guestPreference = new GuestPreferenceEntity(guestId, preSortOrder)
        GuestPreferenceEntity expected = new GuestPreferenceEntity(guestId, postSortOrder)

        when:
        def actual = guestListOrderManager.saveNewOrder(guestId, listId).block()

        then:
        1 * guestListRepository.findGuestPreference(guestId) >> Mono.just(guestPreference)
        1 * guestListRepository.saveGuestPreference(expected) >> Mono.just(expected)
        actual == expected
    }

    def "Test saveNewOrder() errors out when getting the guest record"() {
        given:
        def listId = Uuids.timeBased()

        when:
        guestListOrderManager.saveNewOrder(guestId, listId).block()

        then:
        1 * guestListRepository.findGuestPreference(guestId) >> Mono.error(new RuntimeException("some error"))
        thrown(RuntimeException)
    }

    def "Test saveNewOrder() errors out when saving the guest record"() {
        given:
        def listId = Uuids.timeBased()
        GuestPreferenceEntity postGuestPreference = new GuestPreferenceEntity(guestId, listId.toString())

        when:
        guestListOrderManager.saveNewOrder(guestId, listId).block()

        then:
        1 * guestListRepository.findGuestPreference(guestId) >> Mono.empty()
        1 * guestListRepository.saveGuestPreference(postGuestPreference) >> Mono.error(new RuntimeException("some exception"))
        thrown(RuntimeException)
    }

    def "Test updateSortOrder() when moving listId3 to position above listId1"() {
        given:
        def listId1 = Uuids.timeBased()
        def listId2 = Uuids.timeBased()
        def listId3 = Uuids.timeBased()
        def preSortOrder = listId1.toString() + "," + listId2.toString() + "," + listId3.toString()
        GuestPreferenceEntity guestList = new GuestPreferenceEntity(guestId, preSortOrder)
        def postSortOrder = listId3.toString() + "," + listId1.toString() + "," + listId2.toString()
        GuestPreferenceEntity guestListPostUpdate = new GuestPreferenceEntity(guestId, postSortOrder)

        when:
        guestListOrderManager.updateSortOrder(guestId, listId3, listId1, Direction.ABOVE).block()

        then:
        1 * guestListRepository.findGuestPreference(guestId) >> Mono.just(guestList)
        1 * guestListRepository.saveGuestPreference(guestListPostUpdate) >> Mono.just(guestListPostUpdate)
    }

    def "Test updateSortOrder() when moving listId1 to position below listId3"() {
        given:
        def listId1 = Uuids.timeBased()
        def listId2 = Uuids.timeBased()
        def listId3 = Uuids.timeBased()
        def preSortOrder = listId1.toString() + "," + listId2.toString() + "," + listId3.toString()
        GuestPreferenceEntity guestList = new GuestPreferenceEntity(guestId, preSortOrder)
        def postSortOrder = listId2.toString() + "," + listId3.toString() + "," + listId1.toString()
        GuestPreferenceEntity guestListPostUpdate = new GuestPreferenceEntity(guestId, postSortOrder)

        when:
        guestListOrderManager.updateSortOrder(guestId, listId1, listId3, Direction.BELOW).block()

        then:
        1 * guestListRepository.findGuestPreference(guestId) >> Mono.just(guestList)
        1 * guestListRepository.saveGuestPreference(guestListPostUpdate) >> Mono.just(guestListPostUpdate)
    }

    def "Test updateSortOrder() when moving listId3 to position above listId2"() {
        given:
        def listId1 = Uuids.timeBased()
        def listId2 = Uuids.timeBased()
        def listId3 = Uuids.timeBased()
        def listId4 = Uuids.timeBased()
        def preSortOrder = listId1.toString() + "," + listId2.toString() + "," + listId3.toString() + "," + listId4.toString()
        GuestPreferenceEntity guestList = new GuestPreferenceEntity(guestId, preSortOrder)
        def postSortOrder = listId1.toString() + "," + listId3.toString() + "," + listId2.toString() + "," + listId4.toString()
        GuestPreferenceEntity guestListPostUpdate = new GuestPreferenceEntity(guestId, postSortOrder)

        when:
        guestListOrderManager.updateSortOrder(guestId, listId3, listId2, Direction.ABOVE).block()

        then:
        1 * guestListRepository.findGuestPreference(guestId) >> Mono.just(guestList)
        1 * guestListRepository.saveGuestPreference(guestListPostUpdate) >> Mono.just(guestListPostUpdate)
    }

    def "Test updateSortOrder() when moving listId4 to position below listId2"() {
        given:
        def listId1 = Uuids.timeBased()
        def listId2 = Uuids.timeBased()
        def listId3 = Uuids.timeBased()
        def listId4 = Uuids.timeBased()
        def preSortOrder = listId1.toString() + "," + listId2.toString() + "," + listId3.toString() + "," + listId4.toString()
        GuestPreferenceEntity guestList = new GuestPreferenceEntity(guestId, preSortOrder)
        def postSortOrder = listId1.toString() + "," + listId2.toString() + "," + listId4.toString() + "," + listId3.toString()
        GuestPreferenceEntity guestListPostUpdate = new GuestPreferenceEntity(guestId, postSortOrder)

        when:
        guestListOrderManager.updateSortOrder(guestId, listId4, listId2, Direction.BELOW).block()

        then:
        1 * guestListRepository.findGuestPreference(guestId) >> Mono.just(guestList)
        1 * guestListRepository.saveGuestPreference(guestListPostUpdate) >> Mono.just(guestListPostUpdate)
    }

    def "Test updateSortOrder() when moving listId 1 to position of listId 3 where listId 3 not present"() {
        given:
        def listId1 = Uuids.timeBased()
        def listId2 = Uuids.timeBased()
        def listId3 = Uuids.timeBased()
        def preSortOrder = listId1.toString() + "," + listId2.toString()
        def postSortOrder = listId1.toString() + "," + listId3.toString() + "," + listId2.toString()
        GuestPreferenceEntity guestList = new GuestPreferenceEntity(guestId, preSortOrder)
        GuestPreferenceEntity expected = new GuestPreferenceEntity(guestId, postSortOrder)

        when:
        def actual = guestListOrderManager.updateSortOrder(guestId, listId1, listId3, Direction.ABOVE).block()

        then:
        actual == expected
        1 * guestListRepository.findGuestPreference(guestId) >> Mono.just(guestList)
        1 * guestListRepository.saveGuestPreference(expected) >> Mono.just(expected)
    }

    def "Test removeListIdFromSortOrder() when removing listId2 from sort order"() {
        given:
        def listId1 = Uuids.timeBased()
        def listId2 = Uuids.timeBased()
        def listId3 = Uuids.timeBased()
        def preSortOrder = listId1.toString() + "," + listId2.toString() + "," + listId3.toString()
        GuestPreferenceEntity guestList = new GuestPreferenceEntity(guestId, preSortOrder)
        def postSortOrder = listId1.toString() + "," + listId3.toString()
        GuestPreferenceEntity guestListPostUpdate = new GuestPreferenceEntity(guestId, postSortOrder)

        when:
        guestListOrderManager.removeListIdFromSortOrder(guestId, listId2).block()

        then:
        1 * guestListRepository.findGuestPreference(guestId) >> Mono.just(guestList)
        1 * guestListRepository.saveGuestPreference(guestListPostUpdate) >> Mono.just(guestListPostUpdate)
    }

    def "Test removeListIdFromSortOrder() when removing listId1 from sort order"() {
        given:
        def listId1 = Uuids.timeBased()
        def listId2 = Uuids.timeBased()
        def listId3 = Uuids.timeBased()
        def preSortOrder = listId1.toString() + "," + listId2.toString() + "," + listId3.toString()
        GuestPreferenceEntity guestList = new GuestPreferenceEntity(guestId, preSortOrder)
        def postSortOrder = listId2.toString() + "," + listId3.toString()
        GuestPreferenceEntity guestListPostUpdate = new GuestPreferenceEntity(guestId, postSortOrder)

        when:
        guestListOrderManager.removeListIdFromSortOrder(guestId, listId1).block()

        then:
        1 * guestListRepository.findGuestPreference(guestId) >> Mono.just(guestList)
        1 * guestListRepository.saveGuestPreference(guestListPostUpdate) >> Mono.just(guestListPostUpdate)
    }

    def "Test removeListIdFromSortOrder() when removing listId3 from sort order"() {
        given:
        def listId1 = Uuids.timeBased()
        def listId2 = Uuids.timeBased()
        def listId3 = Uuids.timeBased()
        def preSortOrder = listId1.toString() + "," + listId2.toString() + "," + listId3.toString()
        GuestPreferenceEntity guestList = new GuestPreferenceEntity(guestId, preSortOrder)
        def postSortOrder = listId1.toString() + "," + listId2.toString()
        GuestPreferenceEntity guestListPostUpdate = new GuestPreferenceEntity(guestId, postSortOrder)

        when:
        guestListOrderManager.removeListIdFromSortOrder(guestId, listId3).block()

        then:
        1 * guestListRepository.findGuestPreference(guestId) >> Mono.just(guestList)
        1 * guestListRepository.saveGuestPreference(guestListPostUpdate) >> Mono.just(guestListPostUpdate)
    }

    def "Test removeListIdFromSortOrder() when removing listId makes the sort order empty"() {
        given:
        def listId1 = Uuids.timeBased()
        def preSortOrder = listId1.toString()
        GuestPreferenceEntity guestList = new GuestPreferenceEntity(guestId, preSortOrder)
        def postSortOrder = ""
        GuestPreferenceEntity guestListPostUpdate = new GuestPreferenceEntity(guestId, postSortOrder)

        when:
        guestListOrderManager.removeListIdFromSortOrder(guestId, listId1).block()

        then:
        1 * guestListRepository.findGuestPreference(guestId) >> Mono.just(guestList)
        1 * guestListRepository.saveGuestPreference(guestListPostUpdate) >> Mono.just(guestListPostUpdate)
    }

    def "Test removeListIdFromSortOrder() when there is no record for the guest"() {
        given:
        def listId1 = Uuids.timeBased()
        when:
        guestListOrderManager.removeListIdFromSortOrder(guestId, listId1).block()

        then:
        1 * guestListRepository.findGuestPreference(guestId) >> Mono.empty()
    }

    def "Test getGuestPreference() when getting record by guest id fails"() {
        given:
        def expected = new GuestPreferenceEntity(guestId, "")

        when:
        def actual = guestListOrderManager.getGuestPreference(guestId).block()

        then:
        1 * guestListRepository.findGuestPreference(guestId) >> Mono.error(new RuntimeException("some exception"))
        actual == expected
    }

    def "Test getGuestPreference() when getting record by guest id is not found"() {
        given:
        def expected = new GuestPreferenceEntity(guestId, "")

        when:
        def actual = guestListOrderManager.getGuestPreference(guestId).block()

        then:
        1 * guestListRepository.findGuestPreference(guestId) >> Mono.empty()
        actual == expected
    }
}
