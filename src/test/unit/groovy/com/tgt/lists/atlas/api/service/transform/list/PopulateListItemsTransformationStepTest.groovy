package com.tgt.lists.atlas.api.service.transform.list


import com.tgt.lists.cart.transport.CartContentsResponse
import com.tgt.lists.atlas.api.domain.CartManager
import com.tgt.lists.atlas.api.domain.ContextContainerManager
import com.tgt.lists.atlas.api.service.transform.TransformationContext
import com.tgt.lists.atlas.util.CartDataProvider
import com.tgt.lists.atlas.util.ListDataProvider
import reactor.core.publisher.Mono
import spock.lang.Specification

class PopulateListItemsTransformationStepTest extends Specification {

    ListsTransformationPipeline listsTransformationPipeline
    ListsTransformationPipelineConfiguration transformationPipelineConfiguration
    ListDataProvider listDataProvider
    CartDataProvider cartDataProvider
    TransformationContext transformationContext
    CartManager cartManager
    ContextContainerManager contextContainerManager


    def setup() {
        listsTransformationPipeline = new ListsTransformationPipeline()
        listDataProvider = new ListDataProvider()
        cartDataProvider = new CartDataProvider()
        cartManager = Mock(CartManager)
        contextContainerManager = new ContextContainerManager()
    }

    def "Test executePipeline with populate list items"() {
        given:
        String guestId = "1234"
        UUID listId = UUID.randomUUID()

        // create 5 lists
        def list1 = listDataProvider.getList(UUID.randomUUID(), UUID.randomUUID(), "first-list")
        def list2 = listDataProvider.getList(UUID.randomUUID(), null,"second-list")
        def list3 = listDataProvider.getList(UUID.randomUUID(), UUID.randomUUID(),"third-list")
        def list4 = listDataProvider.getList(UUID.randomUUID(), null, "fourth-list")
        def list5 = listDataProvider.getList(UUID.randomUUID(), UUID.randomUUID(),"fifth-list")

        def lists = [list1,list2,list3,list4,list5]

        // list 1 with 5 items
        CartContentsResponse pendingCartContentsResponseList1 = cartDataProvider.getCartContentsResponse(list1.listId, 3 )
        CartContentsResponse completedCartContentsResponseList1 = cartDataProvider.getCartContentsResponse(list1.listId, 2 )

        CartContentsResponse pendingCartContentsResponseList2 = cartDataProvider.getCartContentsResponse(list1.listId, 4 )

        CartContentsResponse pendingCartContentsResponseList3 = cartDataProvider.getCartContentsResponse(list1.listId, 2 )
        CartContentsResponse completedCartContentsResponseList3 = cartDataProvider.getCartContentsResponse(list1.listId, 1 )

        CartContentsResponse pendingCartContentsResponseList4 = cartDataProvider.getCartContentsResponse(list1.listId, 5 )

        CartContentsResponse pendingCartContentsResponseList5 = cartDataProvider.getCartContentsResponse(list1.listId, 3 )
        CartContentsResponse completedCartContentsResponseList5 = cartDataProvider.getCartContentsResponse(list1.listId, 4 )

        transformationPipelineConfiguration = new ListsTransformationPipelineConfiguration(cartManager, contextContainerManager, null, false)
        transformationContext = new TransformationContext(transformationPipelineConfiguration)
        listsTransformationPipeline.addStep(new PopulateListItemsTransformationStep())

        when:
        def actual = listsTransformationPipeline.executePipeline(guestId, lists, transformationContext).block()

        then:
        1 * cartManager.getListCartContents(list1.listId,true) >> Mono.just(pendingCartContentsResponseList1)
        1 * cartManager.getListCartContents(list1.completedListId,true) >> Mono.just(completedCartContentsResponseList1)

        1 * cartManager.getListCartContents(list2.listId,true) >> Mono.just(pendingCartContentsResponseList2)

        1 * cartManager.getListCartContents(list3.listId,true) >> Mono.just(pendingCartContentsResponseList3)
        1 * cartManager.getListCartContents(list3.completedListId,true) >> Mono.just(completedCartContentsResponseList3)

        1 * cartManager.getListCartContents(list4.listId,true) >> Mono.just(pendingCartContentsResponseList4)

        1 * cartManager.getListCartContents(list5.listId,true) >> Mono.just(pendingCartContentsResponseList5)
        1 * cartManager.getListCartContents(list5.completedListId,true) >> Mono.just(completedCartContentsResponseList5)

        actual.size() == 5
        actual[0].listTitle == "first-list"
        actual[0].pendingItems != null
        actual[0].compeletedItems != null
        actual[0].pendingItemsCount == 3
        actual[0].completedItemsCount == 2
        actual[0].totalItemsCount == 5

        actual[1].listTitle == "second-list"
        actual[1].pendingItems != null
        actual[1].compeletedItems == null
        actual[1].pendingItemsCount == 4
        actual[1].completedItemsCount == 0
        actual[1].totalItemsCount == 4

        actual[2].listTitle == "third-list"
        actual[2].pendingItems != null
        actual[2].compeletedItems != null
        actual[2].pendingItemsCount == 2
        actual[2].completedItemsCount == 1
        actual[2].totalItemsCount == 3

        actual[3].listTitle == "fourth-list"
        actual[3].pendingItems != null
        actual[3].compeletedItems == null
        actual[3].pendingItemsCount == 5
        actual[3].completedItemsCount == 0
        actual[3].totalItemsCount == 5

        actual[4].listTitle == "fifth-list"
        actual[4].pendingItems != null
        actual[4].compeletedItems != null
        actual[4].pendingItemsCount == 3
        actual[4].completedItemsCount == 4
        actual[4].totalItemsCount == 7
    }
}
