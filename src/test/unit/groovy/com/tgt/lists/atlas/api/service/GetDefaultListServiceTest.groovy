package com.tgt.lists.atlas.api.service

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.api.domain.ListItemSortOrderManager
import com.tgt.lists.atlas.api.domain.model.entity.GuestListEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListPreferenceRepository
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.service.transform.list_items.ListItemsTransformationPipeline
import com.tgt.lists.atlas.api.service.transform.list_items.ListItemsTransformationPipelineConfiguration
import com.tgt.lists.atlas.api.service.transform.list_items.SortListItemsTransformationConfiguration
import com.tgt.lists.atlas.api.service.transform.list_items.SortListItemsTransformationStep
import com.tgt.lists.atlas.api.util.*
import com.tgt.lists.atlas.util.ListDataProvider
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Instant

class GetDefaultListServiceTest extends Specification {

    GetListService getListService
    GetDefaultListService getDefaultListService
    ListItemSortOrderManager itemSortOrderManager
    ListRepository listRepository
    ListPreferenceRepository listPreferenceRepository
    ListDataProvider dataProvider

    Long locationId = 1375
    String guestId = "1234"

    def setup() {
        dataProvider = new ListDataProvider()
        listRepository = Mock(ListRepository)
        listPreferenceRepository = Mock(ListPreferenceRepository)
        itemSortOrderManager = new ListItemSortOrderManager(listPreferenceRepository)
        SortListItemsTransformationConfiguration sortListItemsTransformationConfiguration = new SortListItemsTransformationConfiguration(itemSortOrderManager)
        ListItemsTransformationPipelineConfiguration transformationPipelineConfiguration = new ListItemsTransformationPipelineConfiguration(sortListItemsTransformationConfiguration, null)
        getListService = new GetListService(listRepository, transformationPipelineConfiguration)
        getDefaultListService = new GetDefaultListService(listRepository, getListService, "SHOPPING")
    }

    def "Test getDefaultListService() integrity with sub-type being null"() {
        given:
        UUID listId = Uuids.timeBased()
        UUID listItemId1 = Uuids.timeBased()
        UUID listItemId2 = Uuids.timeBased()
        def listTitle = "Testing List Title"
        def listType = "REGISTRY"
        def listSubType = "WEDDING"

        GuestListEntity guestListEntity = new GuestListEntity(guestId, listType, listSubType, LIST_MARKER.DEFAULT.value,
                listId, LIST_STATUS.PENDING.value)

        def tcin1 = "1234"
        def tenantrefId1 = dataProvider.getItemRefId(ItemType.TCIN, tcin1)
        def tcin2 = "1235"
        def tenantrefId2 = dataProvider.getItemRefId(ItemType.TCIN, tcin2)

        def listEntity = dataProvider.createListEntity(listId, listTitle, listType, listSubType, guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())
        def listItemEntity1 = dataProvider.createListItemEntity(listId, listItemId1, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantrefId1, tcin1, "item Title 1", null, null )
        def listItemEntity2 = dataProvider.createListItemEntity(listId, listItemId2, LIST_ITEM_STATE.COMPLETED.value, ItemType.TCIN.value, tenantrefId2, tcin2, "item Title 2", null, null )
        def pendingListEntity = dataProvider.createListItemExtEntity(listEntity, listItemEntity1)
        def completedListEntity = dataProvider.createListItemExtEntity(listEntity, listItemEntity2)

        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))

        when:
        def actual =  getDefaultListService.getDefaultList(guestId, locationId, listItemsTransformationPipeline, ItemIncludeFields.ALL, null).block()

        then:
        listRepository.findGuestListByMarker(_, _, _, _) >> Mono.just(guestListEntity)
        listRepository.findListAndItemsByListId(listId) >> Flux.just([pendingListEntity, completedListEntity].toArray())

        actual.listId == listEntity.id
        actual.channel == listEntity.channel
        actual.listTitle == listEntity.title
        actual.shortDescription == listEntity.description
        actual.listType == listEntity.type
        actual.defaultList

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 1
        pendingItems[0].listItemId == listItemEntity1.itemId
        pendingItems[0].tcin == listItemEntity1.itemTcin
        pendingItems[0].itemTitle == listItemEntity1.itemTitle
        pendingItems[0].itemNote == listItemEntity1.itemNotes
        pendingItems[0].itemType == ItemType.TCIN

        def completedItems = actual.completedListItems
        completedItems.size() == 1
        completedItems[0].listItemId == listItemEntity2.itemId
        completedItems[0].tcin == listItemEntity2.itemTcin
        completedItems[0].itemTitle == listItemEntity2.itemTitle
        completedItems[0].itemNote == listItemEntity2.itemNotes
        completedItems[0].itemType == ItemType.TCIN
    }

    def "Test getDefaultListService() integrity with valid sub-type"() {
        given:
        UUID listId = UUID.randomUUID()
        UUID listItemId1 = UUID.randomUUID()
        UUID listItemId2 = UUID.randomUUID()
        def listTitle = "Testing List Title"
        def listType = "REGISTRY"
        def listSubType = "WEDDING"

        GuestListEntity guestListEntity = new GuestListEntity(guestId, listType, listSubType, LIST_MARKER.DEFAULT.value,
                listId, LIST_STATUS.PENDING.value)

        def tcin1 = "1234"
        def tenantrefId1 = dataProvider.getItemRefId(ItemType.TCIN, tcin1)
        def tcin2 = "1235"
        def tenantrefId2 = dataProvider.getItemRefId(ItemType.TCIN, tcin2)

        def listEntity = dataProvider.createListEntity(listId, listTitle, listType, listSubType, guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())
        def listItemEntity1 = dataProvider.createListItemEntity(listId, listItemId1, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantrefId1, tcin1, "item Title 1", null, null )
        def listItemEntity2 = dataProvider.createListItemEntity(listId, listItemId2, LIST_ITEM_STATE.COMPLETED.value, ItemType.TCIN.value, tenantrefId2, tcin2, "item Title 2", null, null )
        def pendingListEntity = dataProvider.createListItemExtEntity(listEntity, listItemEntity1)
        def completedListEntity = dataProvider.createListItemExtEntity(listEntity, listItemEntity2)

        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))

        when:
        def actual =  getDefaultListService.getDefaultList(guestId, locationId, listItemsTransformationPipeline, ItemIncludeFields.ALL, listSubType).block()

        then:
        listRepository.findGuestListByMarker(_, _, _, _) >> Mono.just(guestListEntity)
        listRepository.findListAndItemsByListId(listId) >> Flux.just([pendingListEntity, completedListEntity].toArray())

        actual.listId == listEntity.id
        actual.channel == listEntity.channel
        actual.listTitle == listEntity.title
        actual.shortDescription == listEntity.description
        actual.listType == listEntity.type
        actual.defaultList

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 1
        pendingItems[0].listItemId == listItemEntity1.itemId
        pendingItems[0].tcin == listItemEntity1.itemTcin
        pendingItems[0].itemTitle == listItemEntity1.itemTitle
        pendingItems[0].itemNote == listItemEntity1.itemNotes
        pendingItems[0].itemType == ItemType.TCIN

        def completedItems = actual.completedListItems
        completedItems.size() == 1
        completedItems[0].listItemId == listItemEntity2.itemId
        completedItems[0].tcin == listItemEntity2.itemTcin
        completedItems[0].itemTitle == listItemEntity2.itemTitle
        completedItems[0].itemNote == listItemEntity2.itemNotes
        completedItems[0].itemType == ItemType.TCIN
    }
}
