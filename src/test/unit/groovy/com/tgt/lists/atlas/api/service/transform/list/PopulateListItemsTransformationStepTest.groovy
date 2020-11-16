package com.tgt.lists.atlas.api.service.transform.list

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.api.domain.ContextContainerManager
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.service.transform.TransformationContext
import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.util.ListDataProvider
import reactor.core.publisher.Flux
import spock.lang.Specification

class PopulateListItemsTransformationStepTest extends Specification {

    ListRepository listRepository
    ListDataProvider listDataProvider
    TransformationContext transformationContext
    ContextContainerManager contextContainerManager
    ListsTransformationPipeline listsTransformationPipeline
    ListsTransformationPipelineConfiguration transformationPipelineConfiguration

    def setup() {
        listsTransformationPipeline = new ListsTransformationPipeline()
        listDataProvider = new ListDataProvider()
        listRepository = Mock(ListRepository)
        contextContainerManager = new ContextContainerManager()
    }

    def "Test executePipeline with populate list items"() {
        given:
        String guestId = "1234"

        // create 5 lists
        def list1 = listDataProvider.getList(Uuids.timeBased(), "first-list")
        def list2 = listDataProvider.getList(Uuids.timeBased(), "second-list")
        def list3 = listDataProvider.getList(Uuids.timeBased(), "third-list")
        def list4 = listDataProvider.getList(Uuids.timeBased(), "fourth-list")
        def list5 = listDataProvider.getList(Uuids.timeBased(), "fifth-list")

        def lists = [list1, list2, list3, list4, list5]

        ListItemEntity listItemEntity11 = listDataProvider.createListItemEntity(list1.listId, Uuids.timeBased(),
                LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn1111", "1111", "new item",
                1, "newItemNote")
        ListItemEntity listItemEntity12 = listDataProvider.createListItemEntity(list1.listId, Uuids.timeBased(),
                LIST_ITEM_STATE.COMPLETED.value, ItemType.TCIN.value, "tcn2222", "2222", "new item",
                1, "newItemNote")

        ListItemEntity listItemEntity21 = listDataProvider.createListItemEntity(list2.listId, Uuids.timeBased(),
                LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn1111", "1111", "new item",
                1, "newItemNote")

        ListItemEntity listItemEntity31 = listDataProvider.createListItemEntity(list3.listId, Uuids.timeBased(),
                LIST_ITEM_STATE.COMPLETED.value, ItemType.TCIN.value, "tcn2222", "2222", "new item",
                1, "newItemNote")

        ListItemEntity listItemEntity41 = listDataProvider.createListItemEntity(list4.listId, Uuids.timeBased(),
                LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn1111", "1111", "new item",
                1, "newItemNote")
        ListItemEntity listItemEntity42 = listDataProvider.createListItemEntity(list4.listId, Uuids.timeBased(),
                LIST_ITEM_STATE.COMPLETED.value, ItemType.TCIN.value, "tcn2222", "2222", "new item",
                1, "newItemNote")
        ListItemEntity listItemEntity43 = listDataProvider.createListItemEntity(list4.listId, Uuids.timeBased(),
                LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn3333", "3333", "new item",
                1, "newItemNote")

        ListItemEntity listItemEntity51 = listDataProvider.createListItemEntity(list5.listId, Uuids.timeBased(),
                LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn3333", "3333", "new item",
                1, "newItemNote")

        transformationPipelineConfiguration = new ListsTransformationPipelineConfiguration(listRepository , contextContainerManager, null)
        transformationContext = new TransformationContext(transformationPipelineConfiguration)
        listsTransformationPipeline.addStep(new PopulateListItemsTransformationStep())

        when:
        def actual = listsTransformationPipeline.executePipeline(guestId, lists, transformationContext).block()

        then:
        1 * listRepository.findListItemsByListId(list1.listId) >> Flux.just(listItemEntity11, listItemEntity12)
        1 * listRepository.findListItemsByListId(list2.listId) >> Flux.just(listItemEntity21)
        1 * listRepository.findListItemsByListId(list3.listId) >> Flux.just(listItemEntity31)
        1 * listRepository.findListItemsByListId(list4.listId) >> Flux.just(listItemEntity41, listItemEntity42, listItemEntity43)
        1 * listRepository.findListItemsByListId(list5.listId) >> Flux.just(listItemEntity51)

        actual.size() == 5
        actual[0].listTitle == "first-list"
        actual[0].pendingItems != null
        actual[0].completedItems != null
        actual[0].pendingItemsCount == 1
        actual[0].completedItemsCount == 1
        actual[0].totalItemsCount == 2

        actual[1].listTitle == "second-list"
        actual[1].pendingItems != null
        actual[1].completedItems == null
        actual[1].pendingItemsCount == 1
        actual[1].completedItemsCount == 0
        actual[1].totalItemsCount == 1

        actual[2].listTitle == "third-list"
        actual[2].pendingItems == null
        actual[2].completedItems != null
        actual[2].pendingItemsCount == 0
        actual[2].completedItemsCount == 1
        actual[2].totalItemsCount == 1

        actual[3].listTitle == "fourth-list"
        actual[3].pendingItems != null
        actual[3].completedItems != null
        actual[3].pendingItemsCount == 2
        actual[3].completedItemsCount == 1
        actual[3].totalItemsCount == 3

        actual[4].listTitle == "fifth-list"
        actual[4].pendingItems != null
        actual[4].completedItems == null
        actual[4].pendingItemsCount == 1
        actual[4].completedItemsCount == 0
        actual[4].totalItemsCount == 1
    }
}
