package com.tgt.lists.atlas.api.async

import com.tgt.lists.atlas.api.domain.GuestPreferenceSortOrderManager
import com.tgt.lists.atlas.api.domain.ListItemSortOrderManager
import com.tgt.lists.atlas.api.domain.model.GuestPreference
import com.tgt.lists.atlas.api.persistence.GuestPreferenceRepository
import com.tgt.lists.atlas.api.persistence.ListRepository
import com.tgt.lists.atlas.api.transport.EditListSortOrderRequestTO
import com.tgt.lists.atlas.api.transport.ListMetaDataTO
import com.tgt.lists.atlas.api.util.Direction
import com.tgt.lists.atlas.api.util.LIST_STATUS
import reactor.core.publisher.Mono
import spock.lang.Specification

class ListSortOrderServiceTest extends Specification {

    GuestPreferenceRepository guestPreferenceRepository
    ListRepository listRepository
    GuestPreferenceSortOrderManager guestPreferenceSortOrderManager
    ListItemSortOrderManager listItemSortOrderManager
    ListSortOrderService listSortOrderService

    def setup() {
        guestPreferenceRepository = Mock(GuestPreferenceRepository)
        listRepository = Mock(ListRepository)
        guestPreferenceSortOrderManager = new GuestPreferenceSortOrderManager(guestPreferenceRepository)
        listItemSortOrderManager = new ListItemSortOrderManager(listRepository)
        listSortOrderService = new ListSortOrderService(guestPreferenceSortOrderManager, listItemSortOrderManager)
    }

    def "Test saveListSortOrder() when list status is completed"() {
        given:
        String guestId = "1234"
        ListMetaDataTO cartMetadata = new ListMetaDataTO(false, LIST_STATUS.COMPLETED)

        when:
        def actual = listSortOrderService.saveListSortOrder(guestId, UUID.randomUUID(), cartMetadata).block()

        then:
        actual
    }

    def "Test saveListSortOrder() when list status is pending"() {
        given:
        String guestId = "1234"
        def listId = UUID.randomUUID()
        ListMetaDataTO cartMetadata = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        GuestPreference guestPreference = new GuestPreference(guestId, listId.toString(), null, null)

        when:
        def actual = listSortOrderService.saveListSortOrder(guestId, listId, cartMetadata).block()

        then:
        1 * guestPreferenceRepository.find(guestId) >> Mono.empty()
        1 * guestPreferenceRepository.save(guestPreference) >> Mono.just(guestPreference)

        actual
    }

    def "Test saveListSortOrder() when list status is pending and getting guest preference fails"() {
        given:
        String guestId = "1234"
        ListMetaDataTO cartMetadata = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        

        when:
        def actual = listSortOrderService.saveListSortOrder(guestId, UUID.randomUUID(), cartMetadata).block()

        then:
        1 * guestPreferenceRepository.find(guestId) >> Mono.error(new RuntimeException("some exception"))

        !actual
    }

    def "Test deleteListSortOrder() when list status is completed"() {
        given:
        String guestId = "1234"
        ListMetaDataTO cartMetadata = new ListMetaDataTO(false, LIST_STATUS.COMPLETED)

        when:
        def actual = listSortOrderService.deleteListSortOrder(guestId, UUID.randomUUID(), cartMetadata).block()

        then:
        actual
    }

    def "Test deleteListSortOrder() when list status is pending"() {
        given:
        String guestId = "1234"
        def listId1 = UUID.randomUUID()
        def listId2 = UUID.randomUUID()
        ListMetaDataTO cartMetadata = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        GuestPreference preGuestPreference = new GuestPreference(guestId, listId1.toString() + "," + listId2.toString(), null, null)
        GuestPreference postGuestPreference = new GuestPreference(guestId, listId2.toString(), null, null)

        when:
        def actual = listSortOrderService.deleteListSortOrder(guestId, listId1, cartMetadata).block()

        then:
        1 * listRepository.delete(listId1) >> Mono.just(1)
        1 * guestPreferenceRepository.find(guestId) >> Mono.just(preGuestPreference)
        1 * guestPreferenceRepository.update(guestId, postGuestPreference.listSortOrder) >> Mono.just(1)
        actual
    }

    def "Test deleteListSortOrder() when list status is pending and no items to delete"() {
        given:
        String guestId = "1234"
        def listId1 = UUID.randomUUID()
        def listId2 = UUID.randomUUID()
        ListMetaDataTO cartMetadata = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        GuestPreference preGuestPreference = new GuestPreference(guestId, listId1.toString() + "," + listId2.toString(), null, null)
        GuestPreference postGuestPreference = new GuestPreference(guestId, listId2.toString(), null, null)

        when:
        def actual = listSortOrderService.deleteListSortOrder(guestId, listId1, cartMetadata).block()

        then:
        1 * listRepository.delete(listId1) >> Mono.empty()
        1 * guestPreferenceRepository.find(guestId) >> Mono.just(preGuestPreference)
        1 * guestPreferenceRepository.update(guestId, postGuestPreference.listSortOrder) >> Mono.just(1)
        actual
    }

    def "Test deleteListSortOrder() when list status is pending and the list id not there in the sort order"() {
        given:
        String guestId = "1234"
        def listId1 = UUID.randomUUID()
        def listId2 = UUID.randomUUID()
        ListMetaDataTO cartMetadata = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        GuestPreference preGuestPreference = new GuestPreference(guestId, listId2.toString(), null, null)

        when:
        def actual = listSortOrderService.deleteListSortOrder(guestId, listId1, cartMetadata).block()

        then:
        1 * listRepository.delete(listId1) >> Mono.just(1)
        1 * guestPreferenceRepository.find(guestId) >> Mono.just(preGuestPreference)

        !actual
    }

    def "Test deleteListSortOrder() when list status is pending and delete list id fails"() {
        given:
        String guestId = "1234"
        def listId1 = UUID.randomUUID()
        ListMetaDataTO cartMetadata = new ListMetaDataTO(false, LIST_STATUS.PENDING)

        when:
        def actual = listSortOrderService.deleteListSortOrder(guestId, listId1, cartMetadata).block()

        then:
        1 * listRepository.delete(listId1) >> Mono.error(new RuntimeException("some exception"))

        !actual
    }

    def "Test deleteListSortOrder() when list status is pending and update list id fails"() {
        given:
        String guestId = "1234"
        def listId1 = UUID.randomUUID()
        def listId2 = UUID.randomUUID()
        ListMetaDataTO cartMetadata = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        GuestPreference preGuestPreference = new GuestPreference(guestId, listId1.toString() + "," + listId2.toString(), null, null)
        GuestPreference postGuestPreference = new GuestPreference(guestId, listId2.toString(), null, null)

        when:
        def actual = listSortOrderService.deleteListSortOrder(guestId, listId1, cartMetadata).block()

        then:
        1 * listRepository.delete(listId1) >> Mono.just(1)
        1 * guestPreferenceRepository.find(guestId) >> Mono.just(preGuestPreference)
        1 * guestPreferenceRepository.update(guestId, postGuestPreference.listSortOrder) >> Mono.error(new RuntimeException("some exception"))

        !actual
    }

    def "Test editListSortOrder() happy path"() {
        given:
        String guestId = "1234"
        def primaryListId = UUID.randomUUID()
        def secondaryListId = UUID.randomUUID()
        def preGuestPreference = new GuestPreference(guestId, primaryListId.toString() + "," + secondaryListId.toString(), null, null)

        when:
        def actual = listSortOrderService.editListSortOrder(guestId, new EditListSortOrderRequestTO(primaryListId, secondaryListId, Direction.ABOVE)).block()

        then:
        1 * guestPreferenceRepository.find(guestId) >> Mono.just(preGuestPreference)

        actual
    }

    def "Test editListSortOrder() when secondary item is not found"() {
        given:
        String guestId = "1234"
        def primaryListId = UUID.randomUUID()
        def secondaryListId = UUID.randomUUID()
        def preGuestPreference = new GuestPreference(guestId, primaryListId.toString(), null, null)
        def postGuestPreference = new GuestPreference(guestId, primaryListId.toString() + "," + secondaryListId.toString(), null, null)

        when:
        def actual = listSortOrderService.editListSortOrder(guestId, new EditListSortOrderRequestTO(primaryListId, secondaryListId, Direction.ABOVE)).block()

        then:
        1 * guestPreferenceRepository.find(guestId) >> Mono.just(preGuestPreference)
        1 * guestPreferenceRepository.update(guestId, postGuestPreference.listSortOrder) >> Mono.just(1)

        actual
    }

    def "Test editListSortOrder() when primary item is not found"() {
        given:
        String guestId = "1234"
        def primaryListId = UUID.randomUUID()
        def secondaryListId = UUID.randomUUID()
        def preGuestPreference = new GuestPreference(guestId, secondaryListId.toString(), null, null)

        when:
        def actual = listSortOrderService.editListSortOrder(guestId, new EditListSortOrderRequestTO(primaryListId, secondaryListId, Direction.ABOVE)).block()

        then:
        1 * guestPreferenceRepository.find(guestId) >> Mono.just(preGuestPreference)

        !actual
    }
}
