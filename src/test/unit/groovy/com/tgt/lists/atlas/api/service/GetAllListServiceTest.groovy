package com.tgt.lists.atlas.api.service

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.api.domain.ContextContainerManager
import com.tgt.lists.atlas.api.domain.GuestPreferenceSortOrderManager
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.persistence.cassandra.GuestPreferenceRepository
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.service.transform.list.ListsTransformationPipeline
import com.tgt.lists.atlas.api.service.transform.list.ListsTransformationPipelineConfiguration
import com.tgt.lists.atlas.api.service.transform.list.PopulateListItemsTransformationStep
import com.tgt.lists.atlas.api.service.transform.list.SortListsTransformationStep
import com.tgt.lists.atlas.api.transport.ListGetAllResponseTO
import com.tgt.lists.atlas.api.util.*
import com.tgt.lists.atlas.util.ListDataProvider
import io.micronaut.http.server.exceptions.InternalServerException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Instant

class GetAllListServiceTest extends Specification {

    GetAllListService getListsService
    GuestPreferenceSortOrderManager guestPreferenceSortOrderManager
    GuestPreferenceRepository guestPreferenceRepository
    ListRepository listRepository
    ContextContainerManager contextContainerManager
    ListDataProvider listDataProvider
    ListsTransformationPipelineConfiguration transformationPipelineConfiguration
    String guestId = "1234"

    def setup() {
        listRepository = Mock(ListRepository)
        guestPreferenceRepository = Mock(GuestPreferenceRepository)
        guestPreferenceSortOrderManager = new GuestPreferenceSortOrderManager(guestPreferenceRepository, listRepository)
        contextContainerManager = new ContextContainerManager()
        transformationPipelineConfiguration = new ListsTransformationPipelineConfiguration(listRepository, contextContainerManager, guestPreferenceSortOrderManager)
        getListsService = new GetAllListService(listRepository, transformationPipelineConfiguration, "SHOPPING", 10)
        listDataProvider = new ListDataProvider()
    }

    def "test getAllListsForUser with one guest list and no list items"() {
        given:
        UUID listId = Uuids.timeBased()
        def listType = "SHOPPING"
        def listSubType = "s"


        def listEntity = listDataProvider.createListEntity(listId, "title", listType, listSubType, guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())

        ListsTransformationPipeline listsTransformationPipeline = new ListsTransformationPipeline()
        listsTransformationPipeline.addStep(new PopulateListItemsTransformationStep()).addStep(new SortListsTransformationStep(ListSortFieldGroup.ADDED_DATE, ListSortOrderGroup.ASCENDING))

        when:
        List<ListGetAllResponseTO> lists = getListsService.getAllListsForUser(
                guestId, listsTransformationPipeline).block()

        then:
        1 * listRepository.findGuestLists(guestId, listType) >> Mono.just([listEntity])
        1 * listRepository.findListItemsByListId(listId) >> Flux.empty()

        lists.size() == 1
        lists[0].totalItemsCount == 0
        lists[0].pendingItemsCount == 0
        lists[0].completedItemsCount == 0
    }

    def "test getAllListsForUser with no lists with listId's received from findGuestLists"() {
        given:
        def listType = "SHOPPING"

        ListsTransformationPipeline listsTransformationPipeline = new ListsTransformationPipeline()
        listsTransformationPipeline.addStep(new PopulateListItemsTransformationStep()).addStep(new SortListsTransformationStep(ListSortFieldGroup.ADDED_DATE, ListSortOrderGroup.ASCENDING))

        when:
        List<ListGetAllResponseTO> lists = getListsService.getAllListsForUser(
                guestId, listsTransformationPipeline).block()

        then:
        1 * listRepository.findGuestLists(guestId, listType) >> Mono.just([])

        lists.size() == 0
    }

    def "test getAllListsForUser with exception from findGuestLists"() {
        given:
        UUID listId = Uuids.timeBased()
        def listType = "SHOPPING"
        def listSubType = "s"

        ListsTransformationPipeline listsTransformationPipeline = new ListsTransformationPipeline()
        listsTransformationPipeline.addStep(new PopulateListItemsTransformationStep()).addStep(new SortListsTransformationStep(ListSortFieldGroup.ADDED_DATE, ListSortOrderGroup.ASCENDING))

        when:
        getListsService.getAllListsForUser(guestId, listsTransformationPipeline).block()

        then:
        1 * listRepository.findGuestLists(guestId, listType) >> Mono.error(new InternalServerException("some error"))

        thrown(InternalServerException)

    }

    def "test getAllListsForUser with one guest list and no PopulateListItemsTransformationStep"() {
        given:
        UUID listId = Uuids.timeBased()
        def listType = "SHOPPING"
        def listSubType = "s"

        def listEntity = listDataProvider.createListEntity(listId, "title", listType, listSubType, guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())

        ListsTransformationPipeline listsTransformationPipeline = new ListsTransformationPipeline()

        when:
        List<ListGetAllResponseTO> lists = getListsService.getAllListsForUser(
                guestId, listsTransformationPipeline).block()

        then:
        1 * listRepository.findGuestLists(guestId, listType) >> Mono.just([listEntity])
        0 * listRepository.findListItemsByListId(listId)

        lists.size() == 1
        lists[0].totalItemsCount == -1
        lists[0].pendingItemsCount == -1
        lists[0].completedItemsCount == -1
    }

    def "test getAllListsForUser with one guest list and PopulateListItemsTransformationStep with list items"() {
        given:
        UUID listId = Uuids.timeBased()
        def listType = "SHOPPING"
        def listSubType = "s"
        def tcin1 = "1234"
        def tenantRefId1 = listDataProvider.getItemRefId(ItemType.TCIN, tcin1)
        def tcin2 = "4567"
        def tenantRefId2 = listDataProvider.getItemRefId(ItemType.TCIN, tcin2)

        def listEntity = listDataProvider.createListEntity(listId, "title", listType, listSubType, guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())
        ListItemEntity listItemEntity1 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantRefId1, tcin1, null, 1, "notes1")
        ListItemEntity listItemEntity2 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.COMPLETED.value, ItemType.TCIN.value, tenantRefId2, tcin2, null, 1, "notes1")

        ListsTransformationPipeline listsTransformationPipeline = new ListsTransformationPipeline()
        listsTransformationPipeline.addStep(new PopulateListItemsTransformationStep())

        when:
        List<ListGetAllResponseTO> lists = getListsService.getAllListsForUser(
                guestId, listsTransformationPipeline).block()

        then:
        1 * listRepository.findGuestLists(guestId, listType) >> Mono.just([listEntity])
        1 * listRepository.findListItemsByListId(listId) >> Flux.just(listItemEntity1, listItemEntity2)

        lists.size() == 1
        lists[0].totalItemsCount == 2
        lists[0].pendingItemsCount == 1
        lists[0].completedItemsCount == 1
    }

    def "test with (ListSortFieldGroup.ADDED_DATE and ListSortOrderGroup.ASCENDING) and (ListSortFieldGroup.ADDED_DATE and ListSortOrderGroup.DESCENDING)"() {
        given:
        UUID listId1 = Uuids.timeBased()
        UUID listId2 = Uuids.timeBased()
        UUID listId3 = Uuids.timeBased()
        UUID listId4 = Uuids.timeBased()
        def listType = "SHOPPING"
        def listSubType = "s"

        def listEntity1 = listDataProvider.createListEntity(listId1, "title1", listType, listSubType, guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())
        def listEntity2 = listDataProvider.createListEntity(listId2, "title2", listType, listSubType, guestId, "", Instant.now(), Instant.now())
        def listEntity3 = listDataProvider.createListEntity(listId3, "title3", listType, listSubType, guestId, "", Instant.now(), Instant.now())
        def listEntity4 = listDataProvider.createListEntity(listId4, "title4", listType, listSubType, guestId, "", Instant.now(), Instant.now())

        ListsTransformationPipeline listsTransformationPipeline = new ListsTransformationPipeline()

        when:
        List<ListGetAllResponseTO> lists1 = getListsService.getAllListsForUser(
                guestId, listsTransformationPipeline.addStep(new SortListsTransformationStep(ListSortFieldGroup.ADDED_DATE, ListSortOrderGroup.ASCENDING))).block()

        then:
        1 * listRepository.findGuestLists(guestId, listType) >> Mono.just([listEntity1, listEntity2, listEntity3, listEntity4])

        lists1.size() == 4
        lists1[0].listId == listId1
        lists1[1].listId == listId2
        lists1[2].listId == listId3
        lists1[3].listId == listId4

        when:
        List<ListGetAllResponseTO> lists2 = getListsService.getAllListsForUser(
                guestId, listsTransformationPipeline.addStep(new SortListsTransformationStep(ListSortFieldGroup.ADDED_DATE, ListSortOrderGroup.DESCENDING))).block()

        then:
        1 * listRepository.findGuestLists(guestId, listType) >> Mono.just([listEntity1, listEntity2, listEntity3, listEntity4])

        lists2.size() == 4
        lists2[0].listId == listId4
        lists2[1].listId == listId3
        lists2[2].listId == listId2
        lists2[3].listId == listId1
    }

    def "test with (ListSortFieldGroup.LIST_TITLE and ListSortOrderGroup.ASCENDING) and (ListSortFieldGroup.LIST_TITLE and ListSortOrderGroup.DESCENDING)"() {
        given:
        UUID listId1 = Uuids.timeBased()
        UUID listId2 = Uuids.timeBased()
        UUID listId3 = Uuids.timeBased()
        UUID listId4 = Uuids.timeBased()
        def listType = "SHOPPING"
        def listSubType = "s"

        def listEntity1 = listDataProvider.createListEntity(listId1, "title1", listType, listSubType, guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())
        def listEntity2 = listDataProvider.createListEntity(listId2, "title2", listType, listSubType, guestId, "", Instant.now(), Instant.now())
        def listEntity3 = listDataProvider.createListEntity(listId3, "title3", listType, listSubType, guestId, "", Instant.now(), Instant.now())
        def listEntity4 = listDataProvider.createListEntity(listId4, "title4", listType, listSubType, guestId, "", Instant.now(), Instant.now())

        ListsTransformationPipeline listsTransformationPipeline = new ListsTransformationPipeline()

        when:
        List<ListGetAllResponseTO> lists1 = getListsService.getAllListsForUser(
                guestId, listsTransformationPipeline.addStep(new SortListsTransformationStep(ListSortFieldGroup.LIST_TITLE, ListSortOrderGroup.ASCENDING))).block()

        then:
        1 * listRepository.findGuestLists(guestId, listType) >> Mono.just([listEntity1, listEntity2, listEntity3, listEntity4])

        lists1.size() == 4
        lists1[0].listId == listId1
        lists1[1].listId == listId2
        lists1[2].listId == listId3
        lists1[3].listId == listId4

        when:
        List<ListGetAllResponseTO> lists2 = getListsService.getAllListsForUser(
                guestId, listsTransformationPipeline.addStep(new SortListsTransformationStep(ListSortFieldGroup.LIST_TITLE, ListSortOrderGroup.DESCENDING))).block()

        then:
        1 * listRepository.findGuestLists(guestId, listType) >> Mono.just([listEntity1, listEntity2, listEntity3, listEntity4])

        lists2.size() == 4
        lists2[0].listId == listId4
        lists2[1].listId == listId3
        lists2[2].listId == listId2
        lists2[3].listId == listId1
    }

    def "test with ListSortFieldGroup.LIST_POSITION"() {
        given:
        UUID listId1 = Uuids.timeBased()
        UUID listId2 = Uuids.timeBased()
        UUID listId3 = Uuids.timeBased()
        UUID listId4 = Uuids.timeBased()
        def listType = "SHOPPING"
        def listSubType = "s"

        def listEntity1 = listDataProvider.createListEntity(listId1, "title1", listType, listSubType, guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())
        def listEntity2 = listDataProvider.createListEntity(listId2, "title2", listType, listSubType, guestId, "", Instant.now(), Instant.now())
        def listEntity3 = listDataProvider.createListEntity(listId3, "title3", listType, listSubType, guestId, "", Instant.now(), Instant.now())
        def listEntity4 = listDataProvider.createListEntity(listId4, "title4", listType, listSubType, guestId, "", Instant.now(), Instant.now())

        def  guestPreferenceEntity = listDataProvider.createGuestPreferenceEntity(guestId,  listId3.toString() + "," + listId2.toString() + "," + listId4.toString() + "," + listId1.toString())

        ListsTransformationPipeline listsTransformationPipeline = new ListsTransformationPipeline()

        when:
        List<ListGetAllResponseTO> lists1 = getListsService.getAllListsForUser(
                guestId, listsTransformationPipeline.addStep(new SortListsTransformationStep(ListSortFieldGroup.LIST_POSITION, ListSortOrderGroup.ASCENDING))).block()

        then:
        1 * listRepository.findGuestLists(guestId, listType) >> Mono.just([listEntity1, listEntity2, listEntity3, listEntity4])
        1 * guestPreferenceRepository.findGuestPreference(guestId) >> Mono.just(guestPreferenceEntity)

        lists1.size() == 4
        lists1[0].listId == listId3
        lists1[1].listId == listId2
        lists1[2].listId == listId4
        lists1[3].listId == listId1
    }

    def "test with ListSortFieldGroup.LIST_POSITION with exception"() {
        given:
        UUID listId1 = Uuids.timeBased()
        UUID listId2 = Uuids.timeBased()
        UUID listId3 = Uuids.timeBased()
        UUID listId4 = Uuids.timeBased()
        def listType = "SHOPPING"
        def listSubType = "s"

        def listEntity1 = listDataProvider.createListEntity(listId1, "title1", listType, listSubType, guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())
        def listEntity2 = listDataProvider.createListEntity(listId2, "title2", listType, listSubType, guestId, "", Instant.now(), Instant.now())
        def listEntity3 = listDataProvider.createListEntity(listId3, "title3", listType, listSubType, guestId, "", Instant.now(), Instant.now())
        def listEntity4 = listDataProvider.createListEntity(listId4, "title4", listType, listSubType, guestId, "", Instant.now(), Instant.now())

        ListsTransformationPipeline listsTransformationPipeline = new ListsTransformationPipeline()

        when:
        List<ListGetAllResponseTO> lists1 = getListsService.getAllListsForUser(
                guestId, listsTransformationPipeline.addStep(new SortListsTransformationStep(ListSortFieldGroup.LIST_POSITION, ListSortOrderGroup.ASCENDING))).block()

        then:
        1 * listRepository.findGuestLists(guestId, listType) >> Mono.just([listEntity1, listEntity2, listEntity3, listEntity4])
        1 * guestPreferenceRepository.findGuestPreference(guestId) >> Mono.error(new InternalServerException("some error"))

        lists1.size() == 4
        lists1[0].listId == listId1
        lists1[1].listId == listId2
        lists1[2].listId == listId3
        lists1[3].listId == listId4
    }

    def "test with ListSortFieldGroup.LIST_POSITION with empty sort order response"() {
        given:
        UUID listId1 = Uuids.timeBased()
        UUID listId2 = Uuids.timeBased()
        UUID listId3 = Uuids.timeBased()
        UUID listId4 = Uuids.timeBased()
        def listType = "SHOPPING"
        def listSubType = "s"

        def listEntity1 = listDataProvider.createListEntity(listId1, "title1", listType, listSubType, guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())
        def listEntity2 = listDataProvider.createListEntity(listId2, "title2", listType, listSubType, guestId, "", Instant.now(), Instant.now())
        def listEntity3 = listDataProvider.createListEntity(listId3, "title3", listType, listSubType, guestId, "", Instant.now(), Instant.now())
        def listEntity4 = listDataProvider.createListEntity(listId4, "title4", listType, listSubType, guestId, "", Instant.now(), Instant.now())

        ListsTransformationPipeline listsTransformationPipeline = new ListsTransformationPipeline()

        when:
        List<ListGetAllResponseTO> lists1 = getListsService.getAllListsForUser(
                guestId, listsTransformationPipeline.addStep(new SortListsTransformationStep(ListSortFieldGroup.LIST_POSITION, ListSortOrderGroup.ASCENDING))).block()

        then:
        1 * listRepository.findGuestLists(guestId, listType) >> Mono.just([listEntity1, listEntity2, listEntity3, listEntity4])
        1 * guestPreferenceRepository.findGuestPreference(guestId) >> Mono.empty()

        lists1.size() == 4
        lists1[0].listId == listId1
        lists1[1].listId == listId2
        lists1[2].listId == listId3
        lists1[3].listId == listId4
    }

    def "test with ListSortFieldGroup.LIST_POSITION and ListSortOrderGroup.ASCENDING with partial response"() {
        given:
        UUID listId1 = Uuids.timeBased()
        UUID listId2 = Uuids.timeBased()
        UUID listId3 = Uuids.timeBased()
        UUID listId4 = Uuids.timeBased()
        def listType = "SHOPPING"
        def listSubType = "s"

        def listEntity1 = listDataProvider.createListEntity(listId1, "title1", listType, listSubType, guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())
        def listEntity2 = listDataProvider.createListEntity(listId2, "title2", listType, listSubType, guestId, "", Instant.now(), Instant.now())
        def listEntity3 = listDataProvider.createListEntity(listId3, "title3", listType, listSubType, guestId, "", Instant.now(), Instant.now())
        def listEntity4 = listDataProvider.createListEntity(listId4, "title4", listType, listSubType, guestId, "", Instant.now(), Instant.now())

        def  guestPreferenceEntity = listDataProvider.createGuestPreferenceEntity(guestId,  listId3.toString() + "," + listId1.toString())

        ListsTransformationPipeline listsTransformationPipeline = new ListsTransformationPipeline()

        when:
        List<ListGetAllResponseTO> lists2 = getListsService.getAllListsForUser(
                guestId, listsTransformationPipeline.addStep(new SortListsTransformationStep(ListSortFieldGroup.LIST_POSITION, ListSortOrderGroup.ASCENDING))).block()

        then:
        1 * listRepository.findGuestLists(guestId, listType) >> Mono.just([listEntity1, listEntity2, listEntity3, listEntity4])
        1 * guestPreferenceRepository.findGuestPreference(guestId) >> Mono.just(guestPreferenceEntity)

        lists2.size() == 4
        lists2[0].listId == listId3
        lists2[1].listId == listId1
        lists2[2].listId == listId2
        lists2[3].listId == listId4
    }
}
