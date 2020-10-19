package com.tgt.lists.atlas.api.domain

import com.tgt.lists.atlas.api.persistence.GuestPreferenceRepository
import com.tgt.lists.atlas.api.domain.model.GuestPreference
import com.tgt.lists.atlas.api.util.Direction
import reactor.core.publisher.Mono
import spock.lang.Specification

class GuestPreferenceSortOrderManagerTest extends Specification {

    GuestPreferenceSortOrderManager guestListOrderManager
    GuestPreferenceRepository guestListRepository
    String guestId = "1234"

    def setup() {
        guestListRepository = Mock(GuestPreferenceRepository)
        guestListOrderManager = new GuestPreferenceSortOrderManager(guestListRepository)
    }

    def "Test saveNewOrder() when the guest does not have any record"() {
        given:
        def listId = UUID.randomUUID()
        GuestPreference expected = new GuestPreference(guestId, listId.toString(), null, null)

        when:
        def actual = guestListOrderManager.saveNewOrder(guestId, listId).block()

        then:
        1 * guestListRepository.find(guestId) >> Mono.empty()
        1 * guestListRepository.save(expected) >> Mono.just(expected)
        actual == expected
    }

    def "Test saveNewOrder() when the guest has record"() {
        given:
        def listId = UUID.randomUUID()
        def preSortOrder = UUID.randomUUID().toString()
        def postSortOrder = listId.toString() + "," + preSortOrder
        GuestPreference guestPreference = new GuestPreference(guestId, preSortOrder, null, null)
        GuestPreference expected = new GuestPreference(guestId, postSortOrder, null, null)

        when:
        def actual = guestListOrderManager.saveNewOrder(guestId, listId).block()

        then:
        1 * guestListRepository.find(guestId) >> Mono.just(guestPreference)
        1 * guestListRepository.update(guestId, expected.listSortOrder) >> Mono.just(1)
        actual == expected
    }

    def "Test saveNewOrder() errors out when getting the guest record"() {
        given:
        def listId = UUID.randomUUID()

        when:
        guestListOrderManager.saveNewOrder(guestId, listId).block()

        then:
        1 * guestListRepository.find(guestId) >> Mono.error(new RuntimeException("some error"))
        thrown(RuntimeException)
    }

    def "Test saveNewOrder() errors out when updating the guest record"() {
        given:
        def listId = UUID.randomUUID()
        def preSortOrder = UUID.randomUUID().toString()
        GuestPreference guestPreference = new GuestPreference(guestId, preSortOrder, null, null)

        when:
        guestListOrderManager.saveNewOrder(guestId, listId).block()

        then:
        1 * guestListRepository.find(guestId) >> Mono.just(guestPreference)
        thrown(RuntimeException)
    }

    def "Test saveNewOrder() errors out when saving the guest record"() {
        given:
        def listId = UUID.randomUUID()
        GuestPreference postGuestPreference = new GuestPreference(guestId, listId.toString(), null, null)

        when:
        guestListOrderManager.saveNewOrder(guestId, listId).block()

        then:
        1 * guestListRepository.find(guestId) >> Mono.empty()
        1 * guestListRepository.save(postGuestPreference) >> Mono.error(new RuntimeException("some exception"))
        thrown(RuntimeException)
    }

    def "Test updateSortOrder() when moving listId3 to position above listId1"() {
        given:
        def listId1 = UUID.randomUUID()
        def listId2 = UUID.randomUUID()
        def listId3 = UUID.randomUUID()
        def preSortOrder = listId1.toString() + "," + listId2.toString() + "," + listId3.toString()
        GuestPreference guestList = new GuestPreference(guestId, preSortOrder, null, null)
        def postSortOrder = listId3.toString() + "," + listId1.toString() + "," + listId2.toString()

        when:
        guestListOrderManager.updateSortOrder(guestId, listId3, listId1, Direction.ABOVE).block()

        then:
        1 * guestListRepository.find(guestId) >> Mono.just(guestList)
        1 * guestListRepository.update(guestId, postSortOrder) >> Mono.just(1)
    }

    def "Test updateSortOrder() when moving listId1 to position below listId3"() {
        given:
        def listId1 = UUID.randomUUID()
        def listId2 = UUID.randomUUID()
        def listId3 = UUID.randomUUID()
        def preSortOrder = listId1.toString() + "," + listId2.toString() + "," + listId3.toString()
        GuestPreference guestList = new GuestPreference(guestId, preSortOrder, null, null)
        def postSortOrder = listId2.toString() + "," + listId3.toString() + "," + listId1.toString()

        when:
        guestListOrderManager.updateSortOrder(guestId, listId1, listId3, Direction.BELOW).block()

        then:
        1 * guestListRepository.find(guestId) >> Mono.just(guestList)
        1 * guestListRepository.update(guestId, postSortOrder) >> Mono.just(1)
    }

    def "Test updateSortOrder() when moving listId3 to position above listId2"() {
        given:
        def listId1 = UUID.randomUUID()
        def listId2 = UUID.randomUUID()
        def listId3 = UUID.randomUUID()
        def listId4 = UUID.randomUUID()
        def preSortOrder = listId1.toString() + "," + listId2.toString() + "," + listId3.toString() + "," + listId4.toString()
        GuestPreference guestList = new GuestPreference(guestId, preSortOrder, null, null)
        def postSortOrder = listId1.toString() + "," + listId3.toString() + "," + listId2.toString() + "," + listId4.toString()

        when:
        guestListOrderManager.updateSortOrder(guestId, listId3, listId2, Direction.ABOVE).block()

        then:
        1 * guestListRepository.find(guestId) >> Mono.just(guestList)
        1 * guestListRepository.update(guestId, postSortOrder) >> Mono.just(1)
    }

    def "Test updateSortOrder() when moving listId4 to position below listId2"() {
        given:
        def listId1 = UUID.randomUUID()
        def listId2 = UUID.randomUUID()
        def listId3 = UUID.randomUUID()
        def listId4 = UUID.randomUUID()
        def preSortOrder = listId1.toString() + "," + listId2.toString() + "," + listId3.toString() + "," + listId4.toString()
        GuestPreference guestList = new GuestPreference(guestId, preSortOrder, null, null)
        def postSortOrder = listId1.toString() + "," + listId2.toString() + "," + listId4.toString() + "," + listId3.toString()

        when:
        guestListOrderManager.updateSortOrder(guestId, listId4, listId2, Direction.BELOW).block()

        then:
        1 * guestListRepository.find(guestId) >> Mono.just(guestList)
        1 * guestListRepository.update(guestId, postSortOrder) >> Mono.just(1)
    }

    def "Test updateSortOrder() when moving listId 1 to position of listId 3 where listId 3 not present"() {
        given:
        def listId1 = UUID.randomUUID()
        def listId2 = UUID.randomUUID()
        def listId3 = UUID.randomUUID()
        def preSortOrder = listId1.toString() + "," + listId2.toString()
        def postSortOrder = listId1.toString() + "," + listId3.toString() + "," + listId2.toString()
        GuestPreference guestList = new GuestPreference(guestId, preSortOrder, null, null)
        GuestPreference expected = new GuestPreference(guestId, postSortOrder, null, null)

        when:
        def actual = guestListOrderManager.updateSortOrder(guestId, listId1, listId3, Direction.ABOVE).block()

        then:
        actual == expected
        1 * guestListRepository.find(guestId) >> Mono.just(guestList)
        1 * guestListRepository.update(guestId, postSortOrder) >> Mono.just(1)
    }

    def "Test removeListIdFromSortOrder() when removing listId2 from sort order"() {
        given:
        def listId1 = UUID.randomUUID()
        def listId2 = UUID.randomUUID()
        def listId3 = UUID.randomUUID()
        def preSortOrder = listId1.toString() + "," + listId2.toString() + "," + listId3.toString()
        GuestPreference guestList = new GuestPreference(guestId, preSortOrder, null, null)
        def postSortOrder = listId1.toString() + "," + listId3.toString()

        when:
        guestListOrderManager.removeListIdFromSortOrder(guestId, listId2).block()

        then:
        1 * guestListRepository.find(guestId) >> Mono.just(guestList)
        1 * guestListRepository.update(guestId, postSortOrder) >> Mono.just(1)
    }

    def "Test removeListIdFromSortOrder() when removing listId1 from sort order"() {
        given:
        def listId1 = UUID.randomUUID()
        def listId2 = UUID.randomUUID()
        def listId3 = UUID.randomUUID()
        def preSortOrder = listId1.toString() + "," + listId2.toString() + "," + listId3.toString()
        GuestPreference guestList = new GuestPreference(guestId, preSortOrder, null, null)
        def postSortOrder = listId2.toString() + "," + listId3.toString()

        when:
        guestListOrderManager.removeListIdFromSortOrder(guestId, listId1).block()

        then:
        1 * guestListRepository.find(guestId) >> Mono.just(guestList)
        1 * guestListRepository.update(guestId, postSortOrder) >> Mono.just(1)
    }

    def "Test removeListIdFromSortOrder() when removing listId3 from sort order"() {
        given:
        def listId1 = UUID.randomUUID()
        def listId2 = UUID.randomUUID()
        def listId3 = UUID.randomUUID()
        def preSortOrder = listId1.toString() + "," + listId2.toString() + "," + listId3.toString()
        GuestPreference guestList = new GuestPreference(guestId, preSortOrder, null, null)
        def postSortOrder = listId1.toString() + "," + listId2.toString()

        when:
        guestListOrderManager.removeListIdFromSortOrder(guestId, listId3).block()

        then:
        1 * guestListRepository.find(guestId) >> Mono.just(guestList)
        1 * guestListRepository.update(guestId, postSortOrder) >> Mono.just(1)
    }

    def "Test removeListIdFromSortOrder() when removing listId makes the sort order empty"() {
        given:
        def listId1 = UUID.randomUUID()
        def preSortOrder = listId1.toString()
        GuestPreference guestList = new GuestPreference(guestId, preSortOrder, null, null)
        def postSortOrder = ""

        when:
        guestListOrderManager.removeListIdFromSortOrder(guestId, listId1).block()

        then:
        1 * guestListRepository.find(guestId) >> Mono.just(guestList)
        1 * guestListRepository.update(guestId, postSortOrder) >> Mono.just(1)
    }

    def "Test removeListIdFromSortOrder() when there is no record for the guest"() {
        given:
        def listId1 = UUID.randomUUID()
        when:
        guestListOrderManager.removeListIdFromSortOrder(guestId, listId1).block()

        then:
        1 * guestListRepository.find(guestId) >> Mono.empty()
    }

    def "Test getGuestPreference() when getting record by guest id fails"() {
        given:
        def expected = new GuestPreference(guestId, "", null, null)

        when:
        def actual = guestListOrderManager.getGuestPreference(guestId).block()

        then:
        1 * guestListRepository.find(guestId) >> Mono.error(new RuntimeException("some exception"))
        actual == expected
    }

    def "Test getGuestPreference() when getting record by guest id is not found"() {
        given:
        def expected = new GuestPreference(guestId, "", null, null)

        when:
        def actual = guestListOrderManager.getGuestPreference(guestId).block()

        then:
        1 * guestListRepository.find(guestId) >> Mono.empty()
        actual == expected
    }
}
