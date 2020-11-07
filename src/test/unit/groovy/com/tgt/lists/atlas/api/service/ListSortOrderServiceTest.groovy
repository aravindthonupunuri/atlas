package com.tgt.lists.atlas.api.service

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.api.domain.GuestPreferenceSortOrderManager
import com.tgt.lists.atlas.api.domain.ListPreferenceSortOrderManager
import com.tgt.lists.atlas.api.domain.model.entity.GuestPreferenceEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListPreferenceEntity
import com.tgt.lists.atlas.api.persistence.cassandra.GuestPreferenceRepository
import com.tgt.lists.atlas.api.persistence.cassandra.ListPreferenceRepository
import com.tgt.lists.atlas.api.transport.EditListSortOrderRequestTO
import com.tgt.lists.atlas.api.util.Direction
import com.tgt.lists.atlas.api.util.LIST_STATE
import reactor.core.publisher.Mono
import spock.lang.Specification

class ListSortOrderServiceTest extends Specification {

    GuestPreferenceRepository guestPreferenceRepository
    ListPreferenceRepository listPreferenceRepository
    GuestPreferenceSortOrderManager guestPreferenceSortOrderManager
    ListPreferenceSortOrderManager listPreferenceSortOrderManager
    ListSortOrderService listSortOrderService

    def setup() {
        guestPreferenceRepository = Mock(GuestPreferenceRepository)
        listPreferenceRepository = Mock(ListPreferenceRepository)
        guestPreferenceSortOrderManager = new GuestPreferenceSortOrderManager(guestPreferenceRepository)
        listPreferenceSortOrderManager = new ListPreferenceSortOrderManager(listPreferenceRepository)
        listSortOrderService = new ListSortOrderService(guestPreferenceSortOrderManager, listPreferenceSortOrderManager)
    }

    def "Test saveListSortOrder() when list status is pending"() {
        given:
        String guestId = "1234"
        def listId = UUID.randomUUID()
        GuestPreferenceEntity guestPreference = new GuestPreferenceEntity(guestId, listId.toString())

        when:
        def actual = listSortOrderService.saveListSortOrder(guestId, listId, LIST_STATE.ACTIVE).block()

        then:
        1 * guestPreferenceRepository.findGuestPreference(guestId) >> Mono.empty()
        1 * guestPreferenceRepository.saveGuestPreference(guestPreference) >> Mono.just(guestPreference)

        actual
    }

    def "Test saveListSortOrder() when list status is pending and getting guest preference fails"() {
        given:
        String guestId = "1234"

        when:
        def actual = listSortOrderService.saveListSortOrder(guestId, UUID.randomUUID(), LIST_STATE.ACTIVE).block()

        then:
        1 * guestPreferenceRepository.findGuestPreference(guestId) >> Mono.error(new RuntimeException("some exception"))

        !actual
    }

    def "Test deleteListSortOrder() when list status is pending"() {
        given:
        String guestId = "1234"
        def listId1 = Uuids.timeBased()
        def listId2 = Uuids.timeBased()
        GuestPreferenceEntity preGuestPreference = new GuestPreferenceEntity(guestId, listId1.toString() + "," + listId2.toString())
        GuestPreferenceEntity postGuestPreference = new GuestPreferenceEntity(guestId, listId2.toString())
        ListPreferenceEntity listPreferenceEntity = new ListPreferenceEntity(listId: listId1, guestId: guestId)

        when:
        def actual = listSortOrderService.deleteListSortOrder(guestId, listId1).block()

        then:
        1 * listPreferenceRepository.deleteListPreferenceByListAndGuestId(listPreferenceEntity) >> Mono.just(listPreferenceEntity)
        1 * guestPreferenceRepository.findGuestPreference(guestId) >> Mono.just(preGuestPreference)
        1 * guestPreferenceRepository.saveGuestPreference(postGuestPreference) >> Mono.just(postGuestPreference)

        actual
    }

    def "Test deleteListSortOrder() when list status is pending and no items to delete - list preference doesnt exist"() {
        given:
        String guestId = "1234"
        def listId1 = Uuids.timeBased()
        def listId2 = Uuids.timeBased()
        GuestPreferenceEntity preGuestPreference = new GuestPreferenceEntity(guestId, listId1.toString() + "," + listId2.toString())
        GuestPreferenceEntity postGuestPreference = new GuestPreferenceEntity(guestId, listId2.toString())
        ListPreferenceEntity listPreferenceEntity = new ListPreferenceEntity(listId: listId1, guestId: guestId)

        when:
        def actual = listSortOrderService.deleteListSortOrder(guestId, listId1).block()

        then:
        1 * listPreferenceRepository.deleteListPreferenceByListAndGuestId(listPreferenceEntity) >> Mono.empty()
        1 * guestPreferenceRepository.findGuestPreference(guestId) >> Mono.just(preGuestPreference)
        1 * guestPreferenceRepository.saveGuestPreference(postGuestPreference) >> Mono.just(postGuestPreference)
        actual
    }

    def "Test deleteListSortOrder() when list status is pending and the list id not there in the sort order"() {
        given:
        String guestId = "1234"
        def listId1 = Uuids.timeBased()
        def listId2 = Uuids.timeBased()
        GuestPreferenceEntity preGuestPreference = new GuestPreferenceEntity(guestId, listId2.toString())
        ListPreferenceEntity listPreferenceEntity = new ListPreferenceEntity(listId: listId1, guestId: guestId)

        when:
        def actual = listSortOrderService.deleteListSortOrder(guestId, listId1).block()

        then:
        1 * listPreferenceRepository.deleteListPreferenceByListAndGuestId(listPreferenceEntity) >> Mono.just(listPreferenceEntity)
        1 * guestPreferenceRepository.findGuestPreference(guestId) >> Mono.just(preGuestPreference)

        !actual
    }

    def "Test deleteListSortOrder() when list status is pending and delete list id fails"() {
        given:
        String guestId = "1234"
        def listId1 = Uuids.timeBased()
        ListPreferenceEntity listPreferenceEntity = new ListPreferenceEntity(listId: listId1, guestId: guestId)

        when:
        def actual = listSortOrderService.deleteListSortOrder(guestId, listId1).block()

        then:
        1 * listPreferenceRepository.deleteListPreferenceByListAndGuestId(listPreferenceEntity) >> Mono.error(new RuntimeException("some exception"))

        !actual
    }

    def "Test deleteListSortOrder() when list status is pending and update list id fails"() {
        given:
        String guestId = "1234"
        def listId1 = Uuids.timeBased()
        def listId2 = Uuids.timeBased()
        GuestPreferenceEntity preGuestPreference = new GuestPreferenceEntity(guestId, listId1.toString() + "," + listId2.toString())
        GuestPreferenceEntity postGuestPreference = new GuestPreferenceEntity(guestId, listId2.toString())
        ListPreferenceEntity listPreferenceEntity = new ListPreferenceEntity(listId: listId1, guestId: guestId)

        when:
        def actual = listSortOrderService.deleteListSortOrder(guestId, listId1).block()

        then:
        1 * listPreferenceRepository.deleteListPreferenceByListAndGuestId(listPreferenceEntity) >> Mono.just(listPreferenceEntity)
        1 * guestPreferenceRepository.findGuestPreference(guestId) >> Mono.just(preGuestPreference)
        1 * guestPreferenceRepository.saveGuestPreference(postGuestPreference) >> Mono.error(new RuntimeException("some exception"))

        !actual
    }

    def "Test editListSortOrder() happy path"() {
        given:
        String guestId = "1234"
        def primaryListId = Uuids.timeBased()
        def secondaryListId = Uuids.timeBased()
        GuestPreferenceEntity preGuestPreference = new GuestPreferenceEntity(guestId, primaryListId.toString() + "," + secondaryListId.toString())

        when:
        def actual = listSortOrderService.editListSortOrder(guestId, new EditListSortOrderRequestTO(primaryListId, secondaryListId, Direction.ABOVE)).block()

        then:
        1 * guestPreferenceRepository.findGuestPreference(guestId) >> Mono.just(preGuestPreference)

        actual
    }

    def "Test editListSortOrder() when secondary item is not found"() {
        given:
        String guestId = "1234"
        def primaryListId = Uuids.timeBased()
        def secondaryListId = Uuids.timeBased()
        def preGuestPreference = new GuestPreferenceEntity(guestId, primaryListId.toString())
        def postGuestPreference = new GuestPreferenceEntity(guestId, primaryListId.toString() + "," + secondaryListId.toString())

        when:
        def actual = listSortOrderService.editListSortOrder(guestId, new EditListSortOrderRequestTO(primaryListId, secondaryListId, Direction.ABOVE)).block()

        then:
        1 * guestPreferenceRepository.findGuestPreference(guestId) >> Mono.just(preGuestPreference)
        1 * guestPreferenceRepository.saveGuestPreference(_) >>  Mono.just(postGuestPreference)

        actual
    }

    def "Test editListSortOrder() when primary item is not found"() {
        given:
        String guestId = "1234"
        def primaryListId = Uuids.timeBased()
        def secondaryListId = Uuids.timeBased()
        def preGuestPreference = new GuestPreferenceEntity(guestId, secondaryListId.toString())
        def postGuestPreference = new GuestPreferenceEntity(guestId, primaryListId.toString() + "," + secondaryListId.toString())

        when:
        def actual = listSortOrderService.editListSortOrder(guestId, new EditListSortOrderRequestTO(primaryListId, secondaryListId, Direction.ABOVE)).block()

        then:
        1 * guestPreferenceRepository.findGuestPreference(guestId) >> Mono.just(preGuestPreference)
        1 * guestPreferenceRepository.saveGuestPreference(_) >>  Mono.just(postGuestPreference)

        actual
    }
}