package com.tgt.lists.atlas.api.service.transform.list_items

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.api.domain.ListPreferenceSortOrderManager
import com.tgt.lists.atlas.api.domain.model.entity.ListPreferenceEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListPreferenceRepository
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
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
    ListPreferenceRepository listPreferenceRepository
    ListDataProvider listDataProvider
    String guestId = "1234"

    def setup() {
        listDataProvider = new ListDataProvider()
        listPreferenceRepository = Mock(ListPreferenceRepository)
        listRepository = Mock(ListRepository)
        ListPreferenceSortOrderManager listPreferenceSortOrderManager = new ListPreferenceSortOrderManager(listPreferenceRepository, listRepository)
        SortListItemsTransformationConfiguration sortListItemsTransformationConfiguration = new SortListItemsTransformationConfiguration(listPreferenceSortOrderManager)
        ListItemsTransformationPipelineConfiguration transformationPipelineContext = new ListItemsTransformationPipelineConfiguration(sortListItemsTransformationConfiguration, null)
        transformationContext = new TransformationContext(transformationPipelineContext)
    }

    def "test sort by title"() {
        given:
        UUID listId = Uuids.timeBased()

        // list with 5 items
        ListItemResponseTO item1 = listDataProvider.getListItem(Uuids.timeBased(), "first")
        ListItemResponseTO item2 = listDataProvider.getListItem(Uuids.timeBased(), "second")
        ListItemResponseTO item3 = listDataProvider.getListItem(Uuids.timeBased(), "third")
        ListItemResponseTO item4 = listDataProvider.getListItem(Uuids.timeBased(), "fourth")
        ListItemResponseTO item5 = listDataProvider.getListItem(Uuids.timeBased(), "fifth")
        List<ListItemResponseTO> itemList = [item1,item2,item3,item4,item5]

        sortListItemsTransformationStep = new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING)

        when:

        def actual = sortListItemsTransformationStep.execute(guestId, listId, itemList, transformationContext).block()

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
        UUID listId = Uuids.timeBased()

        // list with 5 items
        ListItemResponseTO item1 = listDataProvider.getListItem(Uuids.timeBased(), "first")
        ListItemResponseTO item2 = listDataProvider.getListItem(Uuids.timeBased(), "second")
        ListItemResponseTO item3 = listDataProvider.getListItem(Uuids.timeBased(), "third")
        ListItemResponseTO item4 = listDataProvider.getListItem(Uuids.timeBased(), "fourth")
        ListItemResponseTO item5 = listDataProvider.getListItem(Uuids.timeBased(), "fifth")
        List<ListItemResponseTO> itemList = [item1,item2,item3,item4,item5]

        def dbList = new ListPreferenceEntity(listId, guestId, "${item2.listItemId},${item1.listItemId},${item4.listItemId},${item5.listItemId},${item3.listItemId}")

        sortListItemsTransformationStep = new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_POSITION, ItemSortOrderGroup.ASCENDING)
        transformationContext.addContextValue(Constants.LIST_ITEM_STATE_KEY, LIST_ITEM_STATE.PENDING)

        when:

        def actual = sortListItemsTransformationStep.execute(guestId, listId, itemList, transformationContext).block()

        then:
        1 * listPreferenceRepository.getListPreference(_, _) >> Mono.just(dbList)

        actual.size() == 5
        actual[0].itemTitle == "second"
        actual[1].itemTitle == "first"
        actual[2].itemTitle == "fourth"
        actual[3].itemTitle == "fifth"
        actual[4].itemTitle == "third"
    }

    def "test sort by item position for completed item state"() {
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

        sortListItemsTransformationStep = new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_POSITION, ItemSortOrderGroup.ASCENDING)
        transformationContext.addContextValue(Constants.LIST_ITEM_STATE_KEY, LIST_ITEM_STATE.COMPLETED)

        when:

        def actual = sortListItemsTransformationStep.execute(guestId, listId, itemList, transformationContext).block()

        then:
        0 * listPreferenceRepository.getListPreference(_, _) >> Mono.just(dbList)

        actual.size() == 5
        actual[0].itemTitle == "first"
        actual[1].itemTitle == "second"
        actual[2].itemTitle == "third"
        actual[3].itemTitle == "fourth"
        actual[4].itemTitle == "fifth"
    }
}
