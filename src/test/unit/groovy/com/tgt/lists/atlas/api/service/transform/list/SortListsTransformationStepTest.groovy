package com.tgt.lists.atlas.api.service.transform.list

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.api.domain.ContextContainerManager
import com.tgt.lists.atlas.api.domain.GuestPreferenceSortOrderManager
import com.tgt.lists.atlas.api.domain.model.entity.GuestPreferenceEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.service.transform.TransformationContext
import com.tgt.lists.atlas.api.util.ListSortFieldGroup
import com.tgt.lists.atlas.api.util.ListSortOrderGroup
import com.tgt.lists.atlas.util.ListDataProvider
import reactor.core.publisher.Mono
import spock.lang.Specification

class SortListsTransformationStepTest extends Specification {

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

    def "Test executePipeline with sort"() {
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
        listsTransformationPipeline.addStep(new SortListsTransformationStep(ListSortFieldGroup.LIST_TITLE, ListSortOrderGroup.ASCENDING))

        when:
        def actual = listsTransformationPipeline.executePipeline(guestId, lists, transformationContext).block()

        then:
        actual.size() == 5
        actual[0].listTitle == "fifth-list"
        actual[0].pendingItems == null
        actual[0].compeletedItems == null

        actual[1].listTitle == "first-list"
        actual[1].pendingItems == null
        actual[1].compeletedItems == null

        actual[2].listTitle == "fourth-list"
        actual[2].pendingItems == null
        actual[2].compeletedItems == null

        actual[3].listTitle == "second-list"
        actual[3].pendingItems == null
        actual[3].compeletedItems == null

        actual[4].listTitle == "third-list"
        actual[4].pendingItems == null
        actual[4].compeletedItems == null
    }

    def "Test executePipeline with sort by list position"() {
        given:
        String guestId = "1234"

        // create 5 lists
        def list1 = listDataProvider.getList(Uuids.timeBased(), "first-list")
        def list2 = listDataProvider.getList(Uuids.timeBased(), "second-list")
        def list3 = listDataProvider.getList(Uuids.timeBased(), "third-list")
        def list4 = listDataProvider.getList(Uuids.timeBased(), "fourth-list")
        def list5 = listDataProvider.getList(Uuids.timeBased(), "fifth-list")

        def lists = [list1,list2,list3,list4,list5]

        def guestPreference = new GuestPreferenceEntity(guestId, "${list2.listId},${list1.listId},${list4.listId},${list5.listId},${list3.listId}")
        transformationPipelineConfiguration = new ListsTransformationPipelineConfiguration(listRepository, contextContainerManager, guestPreferenceSortOrderManager, true)
        transformationContext = new TransformationContext(transformationPipelineConfiguration)
        listsTransformationPipeline.addStep(new SortListsTransformationStep(ListSortFieldGroup.LIST_POSITION, ListSortOrderGroup.ASCENDING))

        when:
        def actual = listsTransformationPipeline.executePipeline(guestId, lists, transformationContext).block()

        then:
        1 * guestPreferenceSortOrderManager.getGuestPreference(guestId) >> Mono.just(guestPreference)

        actual.size() == 5
        actual[0].listTitle == "second-list"
        actual[1].listTitle == "first-list"
        actual[2].listTitle == "fourth-list"
        actual[3].listTitle == "fifth-list"
        actual[4].listTitle == "third-list"
    }
}
