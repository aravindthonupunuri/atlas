package com.tgt.lists.atlas.api.service.transform.list_items

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.api.domain.ListPreferenceSortOrderManager
import com.tgt.lists.atlas.api.domain.model.entity.ListPreferenceEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListPreferenceRepository
import com.tgt.lists.atlas.api.service.transform.TransformationContext
import com.tgt.lists.atlas.api.transport.ListItemResponseTO
import com.tgt.lists.atlas.api.util.Constants
import com.tgt.lists.atlas.api.util.ItemSortFieldGroup
import com.tgt.lists.atlas.api.util.ItemSortOrderGroup
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.util.ListDataProvider
import org.jetbrains.annotations.NotNull
import reactor.core.publisher.Mono
import spock.lang.Specification

class ListItemsTransformationPipelineTest extends Specification {

    ListItemsTransformationPipeline listItemsTransformationPipeline
    ListPreferenceSortOrderManager listPreferenceSortOrderManager
    ListPreferenceRepository listPreferenceRepository
    ListDataProvider listDataProvider
    TransformationContext transformationContext
    String guestId = "1234"

    def setup() {
        listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listPreferenceRepository = Mock(ListPreferenceRepository)
        listPreferenceSortOrderManager = new ListPreferenceSortOrderManager(listPreferenceRepository)
        listDataProvider = new ListDataProvider()
        def sortListItemsTransformationConfiguration = new SortListItemsTransformationConfiguration(listPreferenceSortOrderManager)
        def paginateListItemsTransformationConfiguration = new PaginateListItemsTransformationConfiguration(2)
        def transformationPipelineConfiguration = new ListItemsTransformationPipelineConfiguration(sortListItemsTransformationConfiguration, paginateListItemsTransformationConfiguration)
        transformationContext = new TransformationContext(transformationPipelineConfiguration)
    }

    def "Test executePipeline without transform"() {
        given:
        UUID listId = Uuids.timeBased()

        // list with 5 items
        ListItemResponseTO item1 = listDataProvider.getListItem(Uuids.timeBased(), "first")
        ListItemResponseTO item2 = listDataProvider.getListItem(Uuids.timeBased(), "second")
        ListItemResponseTO item3 = listDataProvider.getListItem(Uuids.timeBased(), "third")
        ListItemResponseTO item4 = listDataProvider.getListItem(Uuids.timeBased(), "fourth")
        ListItemResponseTO item5 = listDataProvider.getListItem(Uuids.timeBased(), "fifth")
        List<ListItemResponseTO> itemList = [item1,item2,item3,item4,item5]

        when:
        def actual = listItemsTransformationPipeline.executePipeline(guestId, listId, itemList, transformationContext).block()

        then:
        actual.size() == 5
        actual[0].itemTitle == "first"
        actual[1].itemTitle == "second"
        actual[2].itemTitle == "third"
        actual[3].itemTitle == "fourth"
        actual[4].itemTitle == "fifth"
    }

    def "Test executePipeline with sort"() {
        given:
        UUID listId = Uuids.timeBased()

        // list with 5 items
        ListItemResponseTO item1 = listDataProvider.getListItem(Uuids.timeBased(), "first")
        ListItemResponseTO item2 = listDataProvider.getListItem(Uuids.timeBased(), "second")
        ListItemResponseTO item3 = listDataProvider.getListItem(Uuids.timeBased(), "third")
        ListItemResponseTO item4 = listDataProvider.getListItem(Uuids.timeBased(), "fourth")
        ListItemResponseTO item5 = listDataProvider.getListItem(Uuids.timeBased(), "fifth")
        List<ListItemResponseTO> itemList = [item1,item2,item3,item4,item5]

        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))
        transformationContext.addContextValue(Constants.LIST_ITEM_STATE_KEY, LIST_ITEM_STATE.PENDING)

        when:
        def actual = listItemsTransformationPipeline.executePipeline(guestId, listId, itemList, transformationContext).block()

        then:
        actual.size() == 5
        actual[0].itemTitle == "fifth"
        actual[1].itemTitle == "first"
        actual[2].itemTitle == "fourth"
        actual[3].itemTitle == "second"
        actual[4].itemTitle == "third"
    }

    def "Test executePipeline with sort by item position"() {
        given:
        UUID listId = Uuids.timeBased()

        // list with 5 items
        ListItemResponseTO item1 = listDataProvider.getListItem(Uuids.timeBased(), "first")
        ListItemResponseTO item2 = listDataProvider.getListItem(Uuids.timeBased(), "second")
        ListItemResponseTO item3 = listDataProvider.getListItem(Uuids.timeBased(), "third")
        ListItemResponseTO item4 = listDataProvider.getListItem(Uuids.timeBased(), "fourth")
        ListItemResponseTO item5 = listDataProvider.getListItem(Uuids.timeBased(), "fifth")
        List<ListItemResponseTO> itemList = [item1,item2,item3,item4,item5]

        def dbList = new ListPreferenceEntity(listId, guestId, "${item2.listItemId},${item1.listItemId},${item4.listItemId},${item5.listItemId},${item3.listItemId}")

        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_POSITION, ItemSortOrderGroup.ASCENDING))
        transformationContext.addContextValue(Constants.LIST_ITEM_STATE_KEY, LIST_ITEM_STATE.PENDING)

        when:
        def actual = listItemsTransformationPipeline.executePipeline(guestId, listId, itemList, transformationContext).block()

        then:
        1 * listPreferenceRepository.getListPreference(_, _) >> Mono.just(dbList)

        actual.size() == 5
        actual[0].itemTitle == "second"
        actual[1].itemTitle == "first"
        actual[2].itemTitle == "fourth"
        actual[3].itemTitle == "fifth"
        actual[4].itemTitle == "third"
    }

    def "Test executePipeline with sort and page1"() {
        given:
        UUID listId = Uuids.timeBased()

        // list with 5 items
        ListItemResponseTO item1 = listDataProvider.getListItem(Uuids.timeBased(), "first")
        ListItemResponseTO item2 = listDataProvider.getListItem(Uuids.timeBased(), "second")
        ListItemResponseTO item3 = listDataProvider.getListItem(Uuids.timeBased(), "third")
        ListItemResponseTO item4 = listDataProvider.getListItem(Uuids.timeBased(), "fourth")
        ListItemResponseTO item5 = listDataProvider.getListItem(Uuids.timeBased(), "fifth")
        List<ListItemResponseTO> itemList = [item1,item2,item3,item4,item5]

        listItemsTransformationPipeline
                .addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))
                .addStep(new PaginateListItemsTransformationStep(1))

        transformationContext.addContextValue(Constants.LIST_ITEM_STATE_KEY, LIST_ITEM_STATE.PENDING)

        when:
        def actual = listItemsTransformationPipeline.executePipeline(guestId, listId, itemList, transformationContext).block()

        then:
        actual.size() == 2
        actual[0].itemTitle == "fifth"
        actual[1].itemTitle == "first"
        transformationContext.getContextValue(PaginateListItemsTransformationStep.MAX_PAGE_COUNT) == 3
    }

    def "Test executePipeline with sort and page2"() {
        given:
        UUID listId = Uuids.timeBased()

        // list with 5 items
        ListItemResponseTO item1 = listDataProvider.getListItem(Uuids.timeBased(), "first")
        ListItemResponseTO item2 = listDataProvider.getListItem(Uuids.timeBased(), "second")
        ListItemResponseTO item3 = listDataProvider.getListItem(Uuids.timeBased(), "third")
        ListItemResponseTO item4 = listDataProvider.getListItem(Uuids.timeBased(), "fourth")
        ListItemResponseTO item5 = listDataProvider.getListItem(Uuids.timeBased(), "fifth")
        List<ListItemResponseTO> itemList = [item1,item2,item3,item4,item5]

        listItemsTransformationPipeline
                .addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))
                .addStep(new PaginateListItemsTransformationStep(2))

        transformationContext.addContextValue(Constants.LIST_ITEM_STATE_KEY, LIST_ITEM_STATE.PENDING)

        when:
        def actual = listItemsTransformationPipeline.executePipeline(guestId, listId, itemList, transformationContext).block()

        then:
        actual.size() == 2
        actual[0].itemTitle == "fourth"
        actual[1].itemTitle == "second"
    }

    def "Test executePipeline with sort and page3"() {
        given:
        UUID listId = Uuids.timeBased()

        // list with 5 items
        ListItemResponseTO item1 = listDataProvider.getListItem(Uuids.timeBased(), "first")
        ListItemResponseTO item2 = listDataProvider.getListItem(Uuids.timeBased(), "second")
        ListItemResponseTO item3 = listDataProvider.getListItem(Uuids.timeBased(), "third")
        ListItemResponseTO item4 = listDataProvider.getListItem(Uuids.timeBased(), "fourth")
        ListItemResponseTO item5 = listDataProvider.getListItem(Uuids.timeBased(), "fifth")
        List<ListItemResponseTO> itemList = [item1,item2,item3,item4,item5]

        listItemsTransformationPipeline
                .addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))
                .addStep(new PaginateListItemsTransformationStep(3))

        transformationContext.addContextValue(Constants.LIST_ITEM_STATE_KEY, LIST_ITEM_STATE.PENDING)

        when:
        def actual = listItemsTransformationPipeline.executePipeline(guestId, listId, itemList, transformationContext).block()

        then:
        actual.size() == 1
        actual[0].itemTitle == "third"
    }

    def "Test executePipeline with sort and invalid page"() {
        given:
        UUID listId = Uuids.timeBased()

        // list with 5 items
        ListItemResponseTO item1 = listDataProvider.getListItem(Uuids.timeBased(), "first")
        ListItemResponseTO item2 = listDataProvider.getListItem(Uuids.timeBased(), "second")
        ListItemResponseTO item3 = listDataProvider.getListItem(Uuids.timeBased(), "third")
        ListItemResponseTO item4 = listDataProvider.getListItem(Uuids.timeBased(), "fourth")
        ListItemResponseTO item5 = listDataProvider.getListItem(Uuids.timeBased(), "fifth")
        List<ListItemResponseTO> itemList = [item1,item2,item3,item4,item5]

        listItemsTransformationPipeline
                .addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))
                .addStep(new PaginateListItemsTransformationStep(4))

        transformationContext.addContextValue(Constants.LIST_ITEM_STATE_KEY, LIST_ITEM_STATE.PENDING)

        when:
        def actual = listItemsTransformationPipeline.executePipeline(guestId, listId, itemList, transformationContext).block()

        then:
        actual.size() == 0
    }

    def "Test executePipeline with custom transform and page 2"() {
        given:
        UUID listId = Uuids.timeBased()

        // list with 5 items
        ListItemResponseTO item1 = listDataProvider.getListItem(Uuids.timeBased(), "first")
        ListItemResponseTO item2 = listDataProvider.getListItem(Uuids.timeBased(), "second")
        ListItemResponseTO item3 = listDataProvider.getListItem(Uuids.timeBased(), "third")
        ListItemResponseTO item4 = listDataProvider.getListItem(Uuids.timeBased(), "fourth")
        ListItemResponseTO item5 = listDataProvider.getListItem(Uuids.timeBased(), "fifth")
        List<ListItemResponseTO> itemList = [item1,item2,item3,item4,item5]

        listItemsTransformationPipeline
                .addStep(new FilterItemStep("second"))
                .addStep(new PaginateListItemsTransformationStep(2))

        transformationContext.addContextValue(Constants.LIST_ITEM_STATE_KEY, LIST_ITEM_STATE.PENDING)

        when:
        def actual = listItemsTransformationPipeline.executePipeline(guestId, listId, itemList, transformationContext).block()

        then:
        actual.size() == 2
        actual[0].itemTitle == "fourth"
        actual[1].itemTitle == "fifth"
        transformationContext.getContextValue(PaginateListItemsTransformationStep.MAX_PAGE_COUNT) == 2
    }

    def "Test executePipeline with custom transform, sort and page 2"() {
        given:
        UUID listId = Uuids.timeBased()

        // list with 5 items
        ListItemResponseTO item1 = listDataProvider.getListItem(Uuids.timeBased(), "first")
        ListItemResponseTO item2 = listDataProvider.getListItem(Uuids.timeBased(), "second")
        ListItemResponseTO item3 = listDataProvider.getListItem(Uuids.timeBased(), "third")
        ListItemResponseTO item4 = listDataProvider.getListItem(Uuids.timeBased(), "fourth")
        ListItemResponseTO item5 = listDataProvider.getListItem(Uuids.timeBased(), "fifth")
        List<ListItemResponseTO> itemList = [item1,item2,item3,item4,item5]

        listItemsTransformationPipeline
                .addStep(new FilterItemStep("first"))
                .addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))
                .addStep(new PaginateListItemsTransformationStep(2))

        transformationContext.addContextValue(Constants.LIST_ITEM_STATE_KEY, LIST_ITEM_STATE.PENDING)

        when:
        def actual = listItemsTransformationPipeline.executePipeline(guestId, listId, itemList, transformationContext).block()

        then:
        actual.size() == 2
        actual[0].itemTitle == "second"
        actual[1].itemTitle == "third"
        transformationContext.getContextValue(PaginateListItemsTransformationStep.MAX_PAGE_COUNT) == 2
    }

    static class FilterItemStep implements ListItemsTransformationStep {

        String itemName

        FilterItemStep(String itemName) {
            this.itemName = itemName
        }

        @Override
        Mono<List<ListItemResponseTO>> execute(@NotNull String guestId, @NotNull UUID listId, @NotNull List<ListItemResponseTO> items, @NotNull TransformationContext transformationContext) {

            return Mono.fromCallable {
                items.findAll { it.itemTitle != itemName }
            }
        }
    }
}
