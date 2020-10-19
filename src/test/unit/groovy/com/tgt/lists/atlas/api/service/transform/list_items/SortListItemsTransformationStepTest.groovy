package com.tgt.lists.atlas.api.service.transform.list_items

import com.tgt.lists.atlas.api.domain.ListItemSortOrderManager
import com.tgt.lists.atlas.api.persistence.ListRepository
import com.tgt.lists.atlas.api.service.transform.TransformationContext
import com.tgt.lists.atlas.api.transport.ListItemResponseTO
import com.tgt.lists.atlas.api.util.Constants
import com.tgt.lists.atlas.api.util.ItemSortFieldGroup
import com.tgt.lists.atlas.api.util.ItemSortOrderGroup
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.util.ListDataProvider
import reactor.core.publisher.Mono
import spock.lang.Specification

class SortListItemsTransformationStepTest extends Specification {

    SortListItemsTransformationStep sortListItemsTransformationStep
    TransformationContext transformationContext
    ListRepository listRepository
    ListDataProvider listDataProvider

    def setup() {
        listDataProvider = new ListDataProvider()
        listRepository = Mock(ListRepository)
        ListItemSortOrderManager itemSortOrderManager = new ListItemSortOrderManager(listRepository)
        SortListItemsTransformationConfiguration sortListItemsTransformationConfiguration = new SortListItemsTransformationConfiguration(itemSortOrderManager)
        ListItemsTransformationPipelineConfiguration transformationPipelineContext = new ListItemsTransformationPipelineConfiguration(sortListItemsTransformationConfiguration, null)
        transformationContext = new TransformationContext(transformationPipelineContext)
    }

    def "test sort by title"() {
        given:
        UUID listId = UUID.randomUUID()

        // list with 5 items
        ListItemResponseTO item1 = listDataProvider.getListItem(UUID.randomUUID(), "first")
        ListItemResponseTO item2 = listDataProvider.getListItem(UUID.randomUUID(), "second")
        ListItemResponseTO item3 = listDataProvider.getListItem(UUID.randomUUID(), "third")
        ListItemResponseTO item4 = listDataProvider.getListItem(UUID.randomUUID(), "fourth")
        ListItemResponseTO item5 = listDataProvider.getListItem(UUID.randomUUID(), "fifth")
        List<ListItemResponseTO> itemList = [item1,item2,item3,item4,item5]

        sortListItemsTransformationStep = new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING)

        when:

        def actual = sortListItemsTransformationStep.execute(listId, itemList, transformationContext).block()

        then:
        actual.size() == 5
        actual[0].itemTitle == "fifth"
        actual[1].itemTitle == "first"
        actual[2].itemTitle == "fourth"
        actual[3].itemTitle == "second"
        actual[4].itemTitle == "third"
    }

    def "test sort by item position for pending item state"() {
        given:
        UUID listId = UUID.randomUUID()

        // list with 5 items
        ListItemResponseTO item1 = listDataProvider.getListItem(UUID.randomUUID(), "first")
        ListItemResponseTO item2 = listDataProvider.getListItem(UUID.randomUUID(), "second")
        ListItemResponseTO item3 = listDataProvider.getListItem(UUID.randomUUID(), "third")
        ListItemResponseTO item4 = listDataProvider.getListItem(UUID.randomUUID(), "fourth")
        ListItemResponseTO item5 = listDataProvider.getListItem(UUID.randomUUID(), "fifth")
        List<ListItemResponseTO> itemList = [item1,item2,item3,item4,item5]

        def dbList = new com.tgt.lists.atlas.api.domain.model.List(listId, "${item2.listItemId},${item1.listItemId},${item4.listItemId},${item5.listItemId},${item3.listItemId}", null, null)

        sortListItemsTransformationStep = new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_POSITION, ItemSortOrderGroup.ASCENDING)
        transformationContext.addContextValue(Constants.LIST_ITEM_STATE_KEY, LIST_ITEM_STATE.PENDING)

        when:

        def actual = sortListItemsTransformationStep.execute(listId, itemList, transformationContext).block()

        then:
        1 * listRepository.find(listId) >> Mono.just(dbList)

        actual.size() == 5
        actual[0].itemTitle == "second"
        actual[1].itemTitle == "first"
        actual[2].itemTitle == "fourth"
        actual[3].itemTitle == "fifth"
        actual[4].itemTitle == "third"
    }

    def "test sort by item position for completed item state"() {
        given:
        UUID listId = UUID.randomUUID()

        // list with 5 items
        ListItemResponseTO item1 = listDataProvider.getListItem(UUID.randomUUID(), "first")
        ListItemResponseTO item2 = listDataProvider.getListItem(UUID.randomUUID(), "second")
        ListItemResponseTO item3 = listDataProvider.getListItem(UUID.randomUUID(), "third")
        ListItemResponseTO item4 = listDataProvider.getListItem(UUID.randomUUID(), "fourth")
        ListItemResponseTO item5 = listDataProvider.getListItem(UUID.randomUUID(), "fifth")
        List<ListItemResponseTO> itemList = [item1,item2,item3,item4,item5]

        def dbList = new com.tgt.lists.atlas.api.domain.model.List(listId, "${item2.listItemId},${item1.listItemId},${item4.listItemId},${item5.listItemId},${item3.listItemId}", null, null)

        sortListItemsTransformationStep = new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_POSITION, ItemSortOrderGroup.ASCENDING)
        transformationContext.addContextValue(Constants.LIST_ITEM_STATE_KEY, LIST_ITEM_STATE.COMPLETED)

        when:

        def actual = sortListItemsTransformationStep.execute(listId, itemList, transformationContext).block()

        then:
        0 * listRepository.find(listId) >> Mono.just(dbList)

        actual.size() == 5
        actual[0].itemTitle == "first"
        actual[1].itemTitle == "second"
        actual[2].itemTitle == "third"
        actual[3].itemTitle == "fourth"
        actual[4].itemTitle == "fifth"
    }
}
