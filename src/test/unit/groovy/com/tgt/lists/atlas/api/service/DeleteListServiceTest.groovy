package com.tgt.lists.atlas.api.service

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.api.domain.EventPublisher
import com.tgt.lists.atlas.api.domain.GuestPreferenceSortOrderManager
import com.tgt.lists.atlas.api.domain.ListPreferenceSortOrderManager
import com.tgt.lists.atlas.api.domain.model.entity.GuestPreferenceEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListPreferenceEntity
import com.tgt.lists.atlas.api.persistence.cassandra.GuestPreferenceRepository
import com.tgt.lists.atlas.api.persistence.cassandra.ListPreferenceRepository
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.util.ListDataProvider
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Instant

class DeleteListServiceTest extends Specification {

    ListRepository listRepository
    EventPublisher eventPublisher
    DeleteListService deleteListService
    GuestPreferenceRepository guestPreferenceRepository
    ListPreferenceRepository listPreferenceRepository
    ListSortOrderService listSortOrderService
    GuestPreferenceSortOrderManager guestPreferenceSortOrderManager
    ListPreferenceSortOrderManager listPreferenceSortOrderManager
    ListDataProvider listDataProvider
    String guestId = "1234"

    def setup() {
        listRepository = Mock(ListRepository)
        eventPublisher = Mock(EventPublisher)
        guestPreferenceRepository = Mock(GuestPreferenceRepository)
        listPreferenceRepository = Mock(ListPreferenceRepository)
        guestPreferenceSortOrderManager = new GuestPreferenceSortOrderManager(guestPreferenceRepository)
        listPreferenceSortOrderManager = new ListPreferenceSortOrderManager(listPreferenceRepository)
        listSortOrderService = new ListSortOrderService(guestPreferenceSortOrderManager, listPreferenceSortOrderManager)
        deleteListService = new DeleteListService(listRepository, eventPublisher, listSortOrderService)
        listDataProvider = new ListDataProvider()
    }

    def "Test deleteListService when list not found"() {
        given:
        UUID listId = Uuids.timeBased()

        when:
        def actual = deleteListService.deleteList(guestId,listId).block()

        then:
        1 * listRepository.findListById(listId) >> Mono.empty()
        actual.listId == listId
    }

    def "Test deleteListService with exception from findListById"() {
        given:
        UUID listId = Uuids.timeBased()

        when:
        deleteListService.deleteList(guestId,listId).block()

        then:
        1 * listRepository.findListById(listId) >> Mono.error(new RuntimeException("some exception"))
        thrown(RuntimeException)
    }

    def "Test deleteListService with exception from deleteList"() {
        given:
        UUID listId = Uuids.timeBased()
        ListEntity listEntity = listDataProvider.createListEntity(listId, "list title", "shopping", "s", guestId, "d", Instant.now(), Instant.now())

        GuestPreferenceEntity preGuestPreference = new GuestPreferenceEntity(guestId, listId.toString())
        ListPreferenceEntity listPreferenceEntity = new ListPreferenceEntity(listId: listId, guestId: guestId)

        when:
        deleteListService.deleteList(guestId,listId).block()

        then:
        1 * listRepository.findListById(listId) >> Mono.just(listEntity)
        1 * listPreferenceRepository.deleteListPreferenceByListAndGuestId(listPreferenceEntity) >> Mono.just(listPreferenceEntity)
        1 * guestPreferenceRepository.findGuestPreference(guestId) >> Mono.just(preGuestPreference)
        1 * listRepository.deleteList(listEntity) >> Mono.error(new RuntimeException("some exception"))
        thrown(RuntimeException)
    }
}
