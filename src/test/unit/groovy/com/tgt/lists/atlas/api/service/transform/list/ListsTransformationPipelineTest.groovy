package com.tgt.lists.atlas.api.service.transform.list

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.api.domain.ContextContainerManager
import com.tgt.lists.atlas.api.domain.GuestPreferenceSortOrderManager
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.service.transform.TransformationContext
import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.api.util.ListSortFieldGroup
import com.tgt.lists.atlas.api.util.ListSortOrderGroup
import com.tgt.lists.atlas.util.ListDataProvider
import reactor.core.publisher.Flux
import spock.lang.Specification

class ListsTransformationPipelineTest extends Specification {

    ListRepository listRepository
    ListDataProvider listDataProvider
    TransformationContext transformationContext
    ContextContainerManager contextContainerManager
    ListsTransformationPipeline listsTransformationPipeline
    GuestPreferenceSortOrderManager guestPreferenceSortOrderManager
    ListsTransformationPipelineConfiguration transformationPipelineConfiguration

    def setup() {
        listsTransformationPipeline = new ListsTransformationPipeline()
        listDataProvider = new ListDataProvider()
        listRepository = Mock(ListRepository)
        contextContainerManager = new ContextContainerManager()
        guestPreferenceSortOrderManager = Mock(GuestPreferenceSortOrderManager)
    }

    def "Test executePipeline without transform"() {
        given:
        String guestId = "1234"

        // create 5 lists
        def list1 = listDataProvider.getList(Uuids.timeBased(), "first-list")
        def list2 = listDataProvider.getList(Uuids.timeBased(), "second-list")
        def list3 = listDataProvider.getList(Uuids.timeBased(), "third-list")
        def list4 = listDataProvider.getList(Uuids.timeBased(), "fourth-list")
        def list5 = listDataProvider.getList(Uuids.timeBased(), "fifth-list")

        def lists = [list1,list2,list3,list4,list5]

        transformationPipelineConfiguration = new ListsTransformationPipelineConfiguration(listRepository, contextContainerManager, guestPreferenceSortOrderManager, false)
        transformationContext = new TransformationContext(transformationPipelineConfiguration)

        when:
        def actual = listsTransformationPipeline.executePipeline(guestId, lists, transformationContext).block()

        then:
        actual.size() == 5
        actual[0].listTitle == "first-list"
        actual[0].pendingItems == null
        actual[0].completedItems == null

        actual[1].listTitle == "second-list"
        actual[1].pendingItems == null
        actual[1].completedItems == null

        actual[2].listTitle == "third-list"
        actual[2].pendingItems == null
        actual[2].completedItems == null

        actual[3].listTitle == "fourth-list"
        actual[3].pendingItems == null
        actual[3].completedItems == null

        actual[4].listTitle == "fifth-list"
        actual[4].pendingItems == null
        actual[4].completedItems == null
    }

    def "Test executePipeline with sort and populate list items"() {
        given:
        String guestId = "1234"

        // create 5 lists
        def list1 = listDataProvider.getList(Uuids.timeBased(), "first-list")
        def list2 = listDataProvider.getList(Uuids.timeBased(), "second-list")
        def list3 = listDataProvider.getList(Uuids.timeBased(), "third-list")
        def list4 = listDataProvider.getList(Uuids.timeBased(), "fourth-list")
        def list5 = listDataProvider.getList(Uuids.timeBased(), "fifth-list")

        def lists = [list1,list2,list3,list4,list5]

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(list1.listId, Uuids.timeBased(),
                LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn1111", "1111", "new item",
                1, "newItemNote")

        transformationPipelineConfiguration = new ListsTransformationPipelineConfiguration(listRepository, contextContainerManager, guestPreferenceSortOrderManager, false)
        transformationContext = new TransformationContext(transformationPipelineConfiguration)
        listsTransformationPipeline.addStep(new PopulateListItemsTransformationStep()).addStep(new SortListsTransformationStep(ListSortFieldGroup.LIST_TITLE, ListSortOrderGroup.ASCENDING))

        when:
        def actual = listsTransformationPipeline.executePipeline(guestId, lists, transformationContext).block()

        then:
        1 * listRepository.findListItemsByListId(list1.listId) >> Flux.just(listItemEntity)
        1 * listRepository.findListItemsByListId(list2.listId) >> Flux.empty()
        1 * listRepository.findListItemsByListId(list3.listId) >> Flux.just(listItemEntity)
        1 * listRepository.findListItemsByListId(list4.listId) >> Flux.empty()
        1 * listRepository.findListItemsByListId(list5.listId) >> Flux.just(listItemEntity)

        actual.size() == 5

        actual[0].listTitle == "fifth-list"
        actual[0].pendingItems != null
        actual[0].completedItems == null
        actual[0].pendingItemsCount == 1
        actual[0].completedItemsCount == 0

        actual[1].listTitle == "first-list"
        actual[0].pendingItems != null
        actual[0].completedItems == null
        actual[0].pendingItemsCount == 1
        actual[0].completedItemsCount == 0

        actual[2].listTitle == "fourth-list"
        actual[2].pendingItems == null
        actual[2].completedItems == null
        actual[2].pendingItemsCount == 0
        actual[2].completedItemsCount == 0

        actual[3].listTitle == "second-list"
        actual[2].pendingItems == null
        actual[2].completedItems == null
        actual[2].pendingItemsCount == 0
        actual[2].completedItemsCount == 0

        actual[4].listTitle == "third-list"
        actual[0].pendingItems != null
        actual[0].completedItems == null
        actual[0].pendingItemsCount == 1
        actual[0].completedItemsCount == 0
    }
}
