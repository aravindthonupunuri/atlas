package com.tgt.lists.atlas.api.service

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.api.domain.ListPreferenceSortOrderManager
import com.tgt.lists.atlas.api.domain.model.entity.ListPreferenceEntity
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
import java.time.temporal.ChronoUnit

class GetListServiceTest extends Specification {

    GetListService getListService
    ListPreferenceSortOrderManager listPreferenceSortOrderManager
    ListRepository listRepository
    ListPreferenceRepository listPreferenceRepository
    ListDataProvider dataProvider

    Long locationId = 1375
    String guestId = "1234"

    def setup() {
        dataProvider = new ListDataProvider()
        listRepository = Mock(ListRepository)
        listPreferenceRepository = Mock(ListPreferenceRepository)
        listPreferenceSortOrderManager = new ListPreferenceSortOrderManager(listPreferenceRepository, listRepository)
        SortListItemsTransformationConfiguration sortListItemsTransformationConfiguration = new SortListItemsTransformationConfiguration(listPreferenceSortOrderManager)
        ListItemsTransformationPipelineConfiguration transformationPipelineConfiguration = new ListItemsTransformationPipelineConfiguration(sortListItemsTransformationConfiguration, null)
        getListService = new GetListService(listRepository, transformationPipelineConfiguration)
    }

    def "Test getListService() integrity ItemIncludeFields = ALL with 1 of each pending and completed"() {
        given:
        UUID listId = Uuids.timeBased()
        UUID listItemId1 = Uuids.timeBased()
        UUID listItemId2 = Uuids.timeBased()
        def listTitle = "Testing List Title"
        def listType = "REGISTRY"
        def listSubType = "WEDDING"

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
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.ALL).block()

        then:
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

    def "Test getListService() ItemIncludeFields = ALL with no pending items"() {
        given:
        UUID listId = Uuids.timeBased()
        UUID listItemId2 = Uuids.timeBased()
        def listTitle = "Testing List Title"
        def listType = "REGISTRY"
        def listSubType = "WEDDING"

        def tcin2 = "1235"
        def tenantrefId2 = dataProvider.getItemRefId(ItemType.TCIN, tcin2)

        def listEntity = dataProvider.createListEntity(listId, listTitle, listType, listSubType, guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())
        def listItemEntity2 = dataProvider.createListItemEntity(listId, listItemId2, LIST_ITEM_STATE.COMPLETED.value, ItemType.TCIN.value, tenantrefId2, tcin2, "item Title 2", null, null )
        def completedListEntity = dataProvider.createListItemExtEntity(listEntity, listItemEntity2)


        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.ALL).block()

        then:
        listRepository.findListAndItemsByListId(_) >> Flux.just([completedListEntity].toArray())

        actual.listId == listEntity.id
        actual.channel == listEntity.channel
        actual.listTitle == listEntity.title
        actual.shortDescription == listEntity.description
        actual.listType == listEntity.type
        actual.defaultList

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 0

        def completedItems = actual.completedListItems
        completedItems.size() == 1
        completedItems[0].listItemId == listItemEntity2.itemId
        completedItems[0].tcin == listItemEntity2.itemTcin
        completedItems[0].itemTitle == listItemEntity2.itemTitle
        completedItems[0].itemNote == listItemEntity2.itemNotes
        completedItems[0].itemType == ItemType.TCIN
    }

    def "Test getListService() ItemIncludeFields = ALL with no items"() {
        given:
        UUID listId = Uuids.timeBased()
        def listTitle = "Testing List Title"
        def listType = "REGISTRY"
        def listSubType = "WEDDING"

        def listEntity = dataProvider.createListEntity(listId, listTitle, listType, listSubType, guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())


        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.ALL).block()

        then:
        listRepository.findListAndItemsByListId(_) >> Flux.empty()
        listRepository.findListById(listId) >> Mono.just(listEntity)

        actual.listId == listEntity.id
        actual.channel == listEntity.channel
        actual.listTitle == listEntity.title
        actual.shortDescription == listEntity.description
        actual.listType == listEntity.type
        actual.defaultList

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 0

        def completedItems = actual.completedListItems
        completedItems.size() == 0
    }

    def "Test getListService() ItemIncludeFields = PENDING with no items"() {
        given:
        UUID listId = Uuids.timeBased()
        def listTitle = "Testing List Title"
        def listType = "REGISTRY"
        def listSubType = "WEDDING"

        def listEntity = dataProvider.createListEntity(listId, listTitle, listType, listSubType, guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())


        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.PENDING).block()

        then:
        listRepository.findListAndItemsByListIdAndItemState(listId, LIST_ITEM_STATE.PENDING.value) >> Flux.empty()
        listRepository.findListById(listId) >> Mono.just(listEntity)

        actual.listId == listEntity.id
        actual.channel == listEntity.channel
        actual.listTitle == listEntity.title
        actual.shortDescription == listEntity.description
        actual.listType == listEntity.type
        actual.defaultList

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 0

        def completedItems = actual.completedListItems
        completedItems.size() == 0
    }

    def "Test getListService() ItemIncludeFields = COMPLETED with no items"() {
        given:
        UUID listId = Uuids.timeBased()
        def listTitle = "Testing List Title"
        def listType = "REGISTRY"
        def listSubType = "WEDDING"

        def listEntity = dataProvider.createListEntity(listId, listTitle, listType, listSubType, guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())


        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.COMPLETED).block()

        then:
        listRepository.findListAndItemsByListIdAndItemState(listId, LIST_ITEM_STATE.COMPLETED.value) >> Flux.empty()
        listRepository.findListById(listId) >> Mono.just(listEntity)

        actual.listId == listEntity.id
        actual.channel == listEntity.channel
        actual.listTitle == listEntity.title
        actual.shortDescription == listEntity.description
        actual.listType == listEntity.type
        actual.defaultList

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 0

        def completedItems = actual.completedListItems
        completedItems.size() == 0
    }

    def "Test getListService() ItemIncludeFields = ALL with no completed items"() {
        given:
        UUID listId = Uuids.timeBased()
        UUID listItemId1 = Uuids.timeBased()
        def listTitle = "Testing List Title"
        def listType = "REGISTRY"
        def listSubType = "WEDDING"

        def tcin1 = "1234"
        def tenantrefId1 = dataProvider.getItemRefId(ItemType.TCIN, tcin1)


        def listEntity = dataProvider.createListEntity(listId, listTitle, listType, listSubType, guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())
        def listItemEntity1 = dataProvider.createListItemEntity(listId, listItemId1, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantrefId1, tcin1, "item Title 1", null, null )
        def pendingListEntity = dataProvider.createListItemExtEntity(listEntity, listItemEntity1)


        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.ALL).block()

        then:
        listRepository.findListAndItemsByListId(listId) >> Flux.just([pendingListEntity].toArray())

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
        completedItems.size() == 0
    }

    def "Test getListService() ItemIncludeFields = PENDING with 1 pending TCIN item"() {
        given:
        UUID listId = Uuids.timeBased()
        UUID listItemId1 = Uuids.timeBased()
        def listTitle = "Testing List Title"
        def listType = "REGISTRY"
        def listSubType = "WEDDING"

        def tcin1 = "1234"
        def tenantrefId1 = dataProvider.getItemRefId(ItemType.TCIN, tcin1)


        def listEntity = dataProvider.createListEntity(listId, listTitle, listType, listSubType, guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())
        def listItemEntity1 = dataProvider.createListItemEntity(listId, listItemId1, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantrefId1, tcin1, "item Title 1", null, null )
        def pendingListEntity = dataProvider.createListItemExtEntity(listEntity, listItemEntity1)


        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.PENDING).block()

        then:
        listRepository.findListAndItemsByListIdAndItemState(listId, LIST_ITEM_STATE.PENDING.value) >> Flux.just([pendingListEntity].toArray())

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
        completedItems.size() == 0
    }

    def "Test getListService() ItemIncludeFields = COMPLETED with 1 completed TCIN item"() {
        given:
        UUID listId = Uuids.timeBased()
        UUID listItemId2 = Uuids.timeBased()
        def listTitle = "Testing List Title"
        def listType = "REGISTRY"
        def listSubType = "WEDDING"

        def tcin2 = "1235"
        def tenantrefId2 = dataProvider.getItemRefId(ItemType.TCIN, tcin2)

        def listEntity = dataProvider.createListEntity(listId, listTitle, listType, listSubType, guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())
        def listItemEntity2 = dataProvider.createListItemEntity(listId, listItemId2, LIST_ITEM_STATE.COMPLETED.value, ItemType.TCIN.value, tenantrefId2, tcin2, "item Title 2", null, null )
        def completedListEntity = dataProvider.createListItemExtEntity(listEntity, listItemEntity2)



        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.COMPLETED).block()

        then:
        listRepository.findListAndItemsByListIdAndItemState(listId, LIST_ITEM_STATE.COMPLETED.value) >> Flux.just([completedListEntity].toArray())

        actual.listId == listEntity.id
        actual.channel == listEntity.channel
        actual.listTitle == listEntity.title
        actual.shortDescription == listEntity.description
        actual.listType == listEntity.type
        actual.defaultList

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 0

        def completedItems = actual.completedListItems
        completedItems.size() == 1
        completedItems[0].listItemId == listItemEntity2.itemId
        completedItems[0].tcin == listItemEntity2.itemTcin
        completedItems[0].itemTitle == listItemEntity2.itemTitle
        completedItems[0].itemNote == listItemEntity2.itemNotes
        completedItems[0].itemType == ItemType.TCIN
    }

    def "Test getListService() item and sorting based on title and descending order with 1 TCIN item and 1 GENERIC item in pending list"() {
        given:
        UUID listId = Uuids.timeBased()
        UUID listItemId1 = Uuids.timeBased()
        UUID listItemId2 = Uuids.timeBased()
        def listTitle = "Testing List Title"
        def listType = "REGISTRY"
        def listSubType = "WEDDING"

        def tcin1 = "1234"
        def tenantrefId1 = dataProvider.getItemRefId(ItemType.TCIN, tcin1)
        def tcin2 = "1235"
        def tenantrefId2 = dataProvider.getItemRefId(ItemType.TCIN, tcin2)

        def listEntity = dataProvider.createListEntity(listId, listTitle, listType, listSubType, guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())
        def listItemEntity1 = dataProvider.createListItemEntity(listId, listItemId1, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantrefId1, tcin1, "item Title 1", null, null )
        def listItemEntity2 = dataProvider.createListItemEntity(listId, listItemId2, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantrefId2, tcin2, "item Title 2", null, null )
        def pendingListEntity = dataProvider.createListItemExtEntity(listEntity, listItemEntity1)
        def pendingListEntity2 = dataProvider.createListItemExtEntity(listEntity, listItemEntity2)


        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.DESCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.ALL).block()

        then:
        listRepository.findListAndItemsByListId(listId) >> Flux.just([pendingListEntity, pendingListEntity2].toArray())

        actual.listId == listEntity.id
        actual.channel == listEntity.channel
        actual.listTitle == listEntity.title
        actual.shortDescription == listEntity.description
        actual.listType == listEntity.type
        actual.defaultList

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 2
        pendingItems[0].listItemId == listItemEntity2.itemId
        pendingItems[0].tcin == listItemEntity2.itemTcin
        pendingItems[0].itemTitle == listItemEntity2.itemTitle
        pendingItems[0].itemNote == listItemEntity2.itemNotes
        pendingItems[0].itemType == ItemType.TCIN

        pendingItems[1].listItemId == listItemEntity1.itemId
        pendingItems[1].tcin == listItemEntity1.itemTcin
        pendingItems[1].itemTitle == listItemEntity1.itemTitle
        pendingItems[1].itemNote == listItemEntity1.itemNotes
        pendingItems[1].itemType == ItemType.TCIN
    }

    def "Test getListService() sorting based on itemCreatedDate with Ascending order"() {
        given:
        UUID listId = Uuids.timeBased()
        UUID listItemId1 = Uuids.timeBased()
        UUID listItemId2 = Uuids.timeBased()
        def listTitle = "Testing List Title"
        def listType = "REGISTRY"
        def listSubType = "WEDDING"
        def currentLocalInstant = dataProvider.getLocalDateTimeInstant()

        def tcin1 = "1234"
        def tenantrefId1 = dataProvider.getItemRefId(ItemType.TCIN, tcin1)
        def tcin2 = "1235"
        def tenantrefId2 = dataProvider.getItemRefId(ItemType.TCIN, tcin2)

        def listItemEntity1CreatedDate = currentLocalInstant.minus(1, ChronoUnit.DAYS)
        def listItemEntity2CreatedDate = currentLocalInstant

        def listEntity = dataProvider.createListEntity(listId, listTitle, listType, listSubType, guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())
        def listItemEntity1 = dataProvider.createListItemEntity(listId, listItemId1, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantrefId1, tcin1, "item Title 1", null, null, listItemEntity1CreatedDate, null )
        def listItemEntity2 = dataProvider.createListItemEntity(listId, listItemId2, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantrefId2, tcin2, "item Title 2", null, null, listItemEntity2CreatedDate, null )
        def pendingListEntity = dataProvider.createListItemExtEntity(listEntity, listItemEntity1)
        def pendingListEntity2 = dataProvider.createListItemExtEntity(listEntity, listItemEntity2)


        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ADDED_DATE, ItemSortOrderGroup.DESCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.ALL).block()

        then:
        listRepository.findListAndItemsByListId(listId) >> Flux.just([pendingListEntity, pendingListEntity2].toArray())

        actual.listId == listEntity.id
        actual.channel == listEntity.channel
        actual.listTitle == listEntity.title
        actual.shortDescription == listEntity.description
        actual.listType == listEntity.type
        actual.defaultList

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 2
        pendingItems[0].listItemId == listItemEntity2.itemId
        pendingItems[0].tcin == listItemEntity2.itemTcin
        pendingItems[0].itemTitle == listItemEntity2.itemTitle
        pendingItems[0].itemNote == listItemEntity2.itemNotes
        pendingItems[0].itemType == ItemType.TCIN

        pendingItems[1].listItemId == listItemEntity1.itemId
        pendingItems[1].tcin == listItemEntity1.itemTcin
        pendingItems[1].itemTitle == listItemEntity1.itemTitle
        pendingItems[1].itemNote == listItemEntity1.itemNotes
        pendingItems[1].itemType == ItemType.TCIN
    }

    def "Test getListService() sorting based on itemUpdated with Ascending order"() {
        given:
        UUID listId = Uuids.timeBased()
        UUID listItemId1 = Uuids.timeBased()
        UUID listItemId2 = Uuids.timeBased()
        def listTitle = "Testing List Title"
        def listType = "REGISTRY"
        def listSubType = "WEDDING"
        def currentLocalInstant = dataProvider.getLocalDateTimeInstant()

        def tcin1 = "1234"
        def tenantrefId1 = dataProvider.getItemRefId(ItemType.TCIN, tcin1)
        def tcin2 = "1235"
        def tenantrefId2 = dataProvider.getItemRefId(ItemType.TCIN, tcin2)

        def listItemEntity1UpdateDate = currentLocalInstant.minus(1, ChronoUnit.DAYS)
        def listItemEntity2UpdateDate = currentLocalInstant

        def listEntity = dataProvider.createListEntity(listId, listTitle, listType, listSubType, guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())
        def listItemEntity1 = dataProvider.createListItemEntity(listId, listItemId1, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantrefId1, tcin1, "item Title 1", null, null, null, listItemEntity2UpdateDate )
        def listItemEntity2 = dataProvider.createListItemEntity(listId, listItemId2, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantrefId2, tcin2, "item Title 2", null, null, null, listItemEntity1UpdateDate )
        def pendingListEntity = dataProvider.createListItemExtEntity(listEntity, listItemEntity1)
        def pendingListEntity2 = dataProvider.createListItemExtEntity(listEntity, listItemEntity2)


        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.LAST_MODIFIED_DATE, ItemSortOrderGroup.ASCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.ALL).block()

        then:
        listRepository.findListAndItemsByListId(listId) >> Flux.just([pendingListEntity, pendingListEntity2].toArray())

        actual.listId == listEntity.id
        actual.channel == listEntity.channel
        actual.listTitle == listEntity.title
        actual.shortDescription == listEntity.description
        actual.listType == listEntity.type
        actual.defaultList

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 2
        pendingItems[0].listItemId == listItemEntity2.itemId
        pendingItems[0].tcin == listItemEntity2.itemTcin
        pendingItems[0].itemTitle == listItemEntity2.itemTitle
        pendingItems[0].itemNote == listItemEntity2.itemNotes
        pendingItems[0].itemType == ItemType.TCIN

        pendingItems[1].listItemId == listItemEntity1.itemId
        pendingItems[1].tcin == listItemEntity1.itemTcin
        pendingItems[1].itemTitle == listItemEntity1.itemTitle
        pendingItems[1].itemNote == listItemEntity1.itemNotes
        pendingItems[1].itemType == ItemType.TCIN
    }

    def "Test getListService() item sorting based on itemPosition with guest preferred Sort order"() {
        given:
        UUID listId = Uuids.timeBased()
        UUID listItemId1 = Uuids.timeBased()
        UUID listItemId2 = Uuids.timeBased()
        def listTitle = "Testing List Title"
        def listType = "REGISTRY"
        def listSubType = "WEDDING"

        def tcin1 = "1234"
        def tenantrefId1 = dataProvider.getItemRefId(ItemType.TCIN, tcin1)
        def tcin2 = "1235"
        def tenantrefId2 = dataProvider.getItemRefId(ItemType.TCIN, tcin2)

        def listEntity = dataProvider.createListEntity(listId, listTitle, listType, listSubType, guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())
        def listItemEntity1 = dataProvider.createListItemEntity(listId, listItemId1, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantrefId1, tcin1, "item Title 1", null, null)
        def listItemEntity2 = dataProvider.createListItemEntity(listId, listItemId2, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantrefId2, tcin2, "item Title 2", null, null)
        def pendingListEntity = dataProvider.createListItemExtEntity(listEntity, listItemEntity1)
        def pendingListEntity2 = dataProvider.createListItemExtEntity(listEntity, listItemEntity2)

        def listPreference = new ListPreferenceEntity(listId, guestId, listItemId2.toString() + "," + listItemId1.toString())

        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_POSITION, ItemSortOrderGroup.ASCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.ALL).block()

        then:
        listRepository.findListAndItemsByListId(listId) >> Flux.just([pendingListEntity, pendingListEntity2].toArray())
        listPreferenceRepository.getListPreference(_, _) >> Mono.just(listPreference)

        actual.listId == listEntity.id
        actual.channel == listEntity.channel
        actual.listTitle == listEntity.title
        actual.shortDescription == listEntity.description
        actual.listType == listEntity.type
        actual.defaultList

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 2
        pendingItems[0].listItemId == listItemEntity2.itemId
        pendingItems[1].listItemId == listItemEntity1.itemId
    }

    def "Test getListService() item sorting based on itemPosition with guest preferred Sort order for empty list"() {
        given:
        UUID listId = Uuids.timeBased()
        UUID listItemId1 = Uuids.timeBased()
        UUID listItemId2 = Uuids.timeBased()
        def listTitle = "Testing List Title"
        def listType = "REGISTRY"
        def listSubType = "WEDDING"

        def tcin1 = "1234"
        def tenantrefId1 = dataProvider.getItemRefId(ItemType.TCIN, tcin1)
        def tcin2 = "1235"
        def tenantrefId2 = dataProvider.getItemRefId(ItemType.TCIN, tcin2)

        def listEntity = dataProvider.createListEntity(listId, listTitle, listType, listSubType, guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())
        def listItemEntity1 = dataProvider.createListItemEntity(listId, listItemId1, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantrefId1, tcin1, "item Title 1", null, null)
        def listItemEntity2 = dataProvider.createListItemEntity(listId, listItemId2, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantrefId2, tcin2, "item Title 2", null, null)
        def pendingListEntity = dataProvider.createListItemExtEntity(listEntity, listItemEntity1)
        def pendingListEntity2 = dataProvider.createListItemExtEntity(listEntity, listItemEntity2)

        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_POSITION, ItemSortOrderGroup.ASCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.ALL).block()

        then:
        listRepository.findListAndItemsByListId(listId) >> Flux.just([pendingListEntity, pendingListEntity2].toArray())
        listPreferenceRepository.getListPreference(_, _) >> Mono.empty()

        actual.listId == listEntity.id
        actual.channel == listEntity.channel
        actual.listTitle == listEntity.title
        actual.shortDescription == listEntity.description
        actual.listType == listEntity.type
        actual.defaultList

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 2
        pendingItems[0].listItemId == listItemEntity1.itemId
        pendingItems[1].listItemId == listItemEntity2.itemId
    }

    def "Test getListService() item sorting based on itemPosition when items are missing in SortOrder but present in Pending Items"() {
        given:
        UUID listId = Uuids.timeBased()
        UUID listItemId1 = Uuids.timeBased()
        UUID listItemId2 = Uuids.timeBased()
        def listTitle = "Testing List Title"
        def listType = "REGISTRY"
        def listSubType = "WEDDING"

        def tcin1 = "1234"
        def tenantrefId1 = dataProvider.getItemRefId(ItemType.TCIN, tcin1)
        def tcin2 = "1235"
        def tenantrefId2 = dataProvider.getItemRefId(ItemType.TCIN, tcin2)

        def listEntity = dataProvider.createListEntity(listId, listTitle, listType, listSubType, guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())
        def listItemEntity1 = dataProvider.createListItemEntity(listId, listItemId1, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantrefId1, tcin1, "item Title 1", null, null)
        def listItemEntity2 = dataProvider.createListItemEntity(listId, listItemId2, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantrefId2, tcin2, "item Title 2", null, null)
        def pendingListEntity = dataProvider.createListItemExtEntity(listEntity, listItemEntity1)
        def pendingListEntity2 = dataProvider.createListItemExtEntity(listEntity, listItemEntity2)

        def listPreference = new ListPreferenceEntity(listId, guestId, listItemId1.toString())

        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_POSITION, ItemSortOrderGroup.ASCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.ALL).block()

        then:
        listRepository.findListAndItemsByListId(listId) >> Flux.just([pendingListEntity, pendingListEntity2].toArray())
        listPreferenceRepository.getListPreference(_, _) >> Mono.just(listPreference)

        actual.listId == listEntity.id
        actual.channel == listEntity.channel
        actual.listTitle == listEntity.title
        actual.shortDescription == listEntity.description
        actual.listType == listEntity.type
        actual.defaultList

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 2
        pendingItems[0].listItemId == listItemEntity1.itemId
        pendingItems[1].listItemId == listItemEntity2.itemId
    }
}
