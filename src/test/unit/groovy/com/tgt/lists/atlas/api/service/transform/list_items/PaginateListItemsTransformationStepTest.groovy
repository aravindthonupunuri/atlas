package com.tgt.lists.atlas.api.service.transform.list_items

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.api.service.transform.TransformationContext
import com.tgt.lists.atlas.api.transport.ListItemResponseTO
import com.tgt.lists.atlas.util.ListDataProvider
import spock.lang.Specification

class PaginateListItemsTransformationStepTest extends Specification {

    PaginateListItemsTransformationStep paginateListItemsTransformationStep
    TransformationContext transformationContext
    ListDataProvider listDataProvider
    String guestId = "1234"

    def setup() {
        listDataProvider = new ListDataProvider()
        PaginateListItemsTransformationConfiguration paginateListItemsTransformationConfiguration = new PaginateListItemsTransformationConfiguration(listDataProvider.getConfiguration(3, 5, 5, true, false, false, false))
        ListItemsTransformationPipelineConfiguration transformationPipelineContext = new ListItemsTransformationPipelineConfiguration(null, paginateListItemsTransformationConfiguration)
        transformationContext = new TransformationContext(transformationPipelineContext)

    }

    def "test execute with page 1"() {
        given:
        UUID listId = Uuids.timeBased()

        // list with 5 items
        ListItemResponseTO item1 = listDataProvider.getListItem(Uuids.timeBased(), "first")
        ListItemResponseTO item2 = listDataProvider.getListItem(Uuids.timeBased(), "second")
        ListItemResponseTO item3 = listDataProvider.getListItem(Uuids.timeBased(), "third")
        ListItemResponseTO item4 = listDataProvider.getListItem(Uuids.timeBased(), "fourth")
        ListItemResponseTO item5 = listDataProvider.getListItem(Uuids.timeBased(), "fifth")
        List<ListItemResponseTO> itemList = [item1,item2,item3,item4,item5]

        paginateListItemsTransformationStep = new PaginateListItemsTransformationStep(1)

        when:

        def actual = paginateListItemsTransformationStep.execute(guestId, listId, itemList, transformationContext).block()

        then:
        actual.size() == 2
        actual[0].itemTitle == "first"
        actual[1].itemTitle == "second"
    }

    def "test execute with page 2"() {
        given:
        UUID listId = Uuids.timeBased()

        // list with 5 items
        ListItemResponseTO item1 = listDataProvider.getListItem(Uuids.timeBased(), "first")
        ListItemResponseTO item2 = listDataProvider.getListItem(Uuids.timeBased(), "second")
        ListItemResponseTO item3 = listDataProvider.getListItem(Uuids.timeBased(), "third")
        ListItemResponseTO item4 = listDataProvider.getListItem(Uuids.timeBased(), "fourth")
        ListItemResponseTO item5 = listDataProvider.getListItem(Uuids.timeBased(), "fifth")
        List<ListItemResponseTO> itemList = [item1,item2,item3,item4,item5]

        paginateListItemsTransformationStep = new PaginateListItemsTransformationStep(2)

        when:

        def actual = paginateListItemsTransformationStep.execute(guestId, listId, itemList, transformationContext).block()

        then:
        actual.size() == 2
        actual[0].itemTitle == "third"
        actual[1].itemTitle == "fourth"
    }

    def "test execute with page 3"() {
        given:
        UUID listId = Uuids.timeBased()

        // list with 5 items
        ListItemResponseTO item1 = listDataProvider.getListItem(Uuids.timeBased(), "first")
        ListItemResponseTO item2 = listDataProvider.getListItem(Uuids.timeBased(), "second")
        ListItemResponseTO item3 = listDataProvider.getListItem(Uuids.timeBased(), "third")
        ListItemResponseTO item4 = listDataProvider.getListItem(Uuids.timeBased(), "fourth")
        ListItemResponseTO item5 = listDataProvider.getListItem(Uuids.timeBased(), "fifth")
        List<ListItemResponseTO> itemList = [item1,item2,item3,item4,item5]

        paginateListItemsTransformationStep = new PaginateListItemsTransformationStep(3)

        when:

        def actual = paginateListItemsTransformationStep.execute(guestId, listId, itemList, transformationContext).block()

        then:
        actual.size() == 1
        actual[0].itemTitle == "fifth"
    }

    def "test execute with invalid page 4"() {
        given:
        UUID listId = Uuids.timeBased()

        // list with 5 items
        ListItemResponseTO item1 = listDataProvider.getListItem(Uuids.timeBased(), "first")
        ListItemResponseTO item2 = listDataProvider.getListItem(Uuids.timeBased(), "second")
        ListItemResponseTO item3 = listDataProvider.getListItem(Uuids.timeBased(), "third")
        ListItemResponseTO item4 = listDataProvider.getListItem(Uuids.timeBased(), "fourth")
        ListItemResponseTO item5 = listDataProvider.getListItem(Uuids.timeBased(), "fifth")
        List<ListItemResponseTO> itemList = [item1,item2,item3,item4,item5]

        paginateListItemsTransformationStep = new PaginateListItemsTransformationStep(4)

        when:

        def actual = paginateListItemsTransformationStep.execute(guestId, listId, itemList, transformationContext).block()

        then:
        actual.size() == 0
    }
}
