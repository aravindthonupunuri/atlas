package com.tgt.lists.atlas.api.service

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.api.domain.model.entity.GuestListEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.EditListSortOrderRequestTO
import com.tgt.lists.atlas.api.util.Direction
import com.tgt.lists.atlas.util.ListDataProvider
import com.tgt.lists.common.components.exception.BadRequestException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

class EditListSortOrderServiceTest extends Specification {

    EditListSortOrderService editListSortOrderService
    ListRepository listRepository
    ListDataProvider listDataProvider
    ListSortOrderService listSortOrderService
    String guestId = "1234"
    String listType = "SHOPPING"

    def setup() {
        listDataProvider = new ListDataProvider()
        listRepository = Mock(ListRepository)
        listSortOrderService = Mock(ListSortOrderService)
        editListSortOrderService = new EditListSortOrderService(listType , listRepository, listSortOrderService)
    }

    def "Test editListPosition() when primary and secondary list id are same"() {
        given:
        UUID listId1 = Uuids.timeBased()
        def listMarker = "d"
        def editSortOrderRequest = new EditListSortOrderRequestTO(listId1, listId1, Direction.BELOW)

        ListEntity listEntity1 = listDataProvider.createListEntity(listId1, "list title", listType, "s", guestId, listMarker)
        GuestListEntity guestListEntity1 = listDataProvider.createGuestListEntity(guestId, listType, listId1, listMarker, null, null)


        when:
        def actual = editListSortOrderService.editListPosition(guestId, editSortOrderRequest).block()

        then:
        1 * listRepository.findListById(listId1) >> Mono.just(listEntity1)
        1 * listRepository.findGuestListByGuestId(_, _) >> Flux.just(guestListEntity1)

        actual
    }

    def "Test editListPosition() when primary and secondary list id are different"() {
        given:
        UUID listId1 = Uuids.timeBased()
        UUID listId2 = Uuids.timeBased()
        def listMarker = "d"
        def editSortOrderRequest = new EditListSortOrderRequestTO(listId1, listId2, Direction.BELOW)

        ListEntity listEntity1 = listDataProvider.createListEntity(listId1, "list title", listType, "s", guestId, listMarker)
        GuestListEntity guestListEntity1 = listDataProvider.createGuestListEntity(guestId, listType, listId1, listMarker, null, null)
        GuestListEntity guestListEntity2 = listDataProvider.createGuestListEntity(guestId, listType, listId2, null, null, null)

        when:
        def actual = editListSortOrderService.editListPosition(guestId, editSortOrderRequest).block()

        then:
        1 * listRepository.findListById(listId1) >> Mono.just(listEntity1)
        1 * listRepository.findGuestListByGuestId(_, _) >> Flux.just(guestListEntity1, guestListEntity2)
        1 * listSortOrderService.editListSortOrder(guestId, editSortOrderRequest) >> Mono.just(true)

        actual
    }

    def "test editListPosition() when unauthorized list ids are passed"() {
        given:
        UUID listId1 = Uuids.timeBased()
        UUID listId2 = Uuids.timeBased()
        def listMarker = "d"
        def editSortOrderRequest = new EditListSortOrderRequestTO(listId1, listId2, Direction.BELOW)

        ListEntity listEntity1 = listDataProvider.createListEntity(listId1, "list title", listType, "s", guestId, listMarker)
        GuestListEntity guestListEntity1 = listDataProvider.createGuestListEntity(guestId, listType, listId1, listMarker, null, null)

        when:
        editListSortOrderService.editListPosition(guestId, editSortOrderRequest).block()

        then:
        1 * listRepository.findListById(listId1) >> Mono.just(listEntity1)
        1 * listRepository.findGuestListByGuestId(_, _) >> Flux.just(guestListEntity1)

        thrown BadRequestException
    }
}
