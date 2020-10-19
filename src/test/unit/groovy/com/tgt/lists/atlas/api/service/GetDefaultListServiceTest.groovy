package com.tgt.lists.atlas.api.service

import com.tgt.lists.cart.transport.CartResponse
import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.atlas.api.domain.CartManager
import com.tgt.lists.atlas.api.domain.ContextContainerManager
import com.tgt.lists.atlas.api.domain.ListItemSortOrderManager
import com.tgt.lists.atlas.api.persistence.ListRepository
import com.tgt.lists.atlas.api.service.transform.list_items.ListItemsTransformationPipeline
import com.tgt.lists.atlas.api.service.transform.list_items.ListItemsTransformationPipelineConfiguration
import com.tgt.lists.atlas.api.service.transform.list_items.SortListItemsTransformationConfiguration
import com.tgt.lists.atlas.api.service.transform.list_items.SortListItemsTransformationStep
import com.tgt.lists.atlas.api.transport.ListItemMetaDataTO
import com.tgt.lists.atlas.api.transport.ListMetaDataTO
import com.tgt.lists.atlas.api.transport.UserItemMetaDataTO
import com.tgt.lists.atlas.api.transport.UserMetaDataTO
import com.tgt.lists.atlas.api.util.*
import com.tgt.lists.atlas.util.CartDataProvider
import com.tgt.lists.atlas.util.TestListChannel
import reactor.core.publisher.Mono
import spock.lang.Specification

class GetDefaultListServiceTest extends Specification {

    GetListService getListService
    GetDefaultListService getDefaultListService
    CartManager cartManager
    CartDataProvider cartDataProvider
    ListItemSortOrderManager itemSortOrderManager
    ListRepository listRepository
    ContextContainerManager contextContainerManager

    Long locationId = 1375
    String guestId = "1234"

    def setup() {
        cartManager = Mock(CartManager)
        listRepository = Mock(ListRepository)
        itemSortOrderManager = new ListItemSortOrderManager(listRepository)
        contextContainerManager = new ContextContainerManager()
        cartDataProvider = new CartDataProvider()
        SortListItemsTransformationConfiguration sortListItemsTransformationConfiguration = new SortListItemsTransformationConfiguration(itemSortOrderManager)
        ListItemsTransformationPipelineConfiguration transformationPipelineConfiguration = new ListItemsTransformationPipelineConfiguration(sortListItemsTransformationConfiguration, null)
        getListService = new GetListService(cartManager, transformationPipelineConfiguration, contextContainerManager, true)
        getDefaultListService = new GetDefaultListService(cartManager, getListService, "SHOPPING")
    }

    def "Test getDefaultListService() integrity"() {
        given:
        UUID listId = UUID.randomUUID()
        UUID listId2 = UUID.randomUUID()

        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "1235"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)

        ListMetaDataTO metadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        ListMetaDataTO metadata2 = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        ListItemMetaDataTO item1MetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        ListItemMetaDataTO item2MetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)

        def cartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), "SHOPPING", CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))
        def cartResponse2 = cartDataProvider.getCartResponse(listId2, guestId, "SHOPPING", listId.toString(),
            cartDataProvider.getMetaData(metadata2, new UserMetaDataTO()))
        List<CartResponse> cartResponseList = [cartResponse, cartResponse2]

        def cartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(item1MetaData, new UserItemMetaDataTO()), null, null, null)
        def cartResult = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse1])

        def cartItemResponse2 = cartDataProvider.getCartItemResponse(listId2, UUID.randomUUID(), tenantrefId2, TestListChannel.WEB.toString(), tcin2,
            "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(item2MetaData, new UserItemMetaDataTO()), null, null, null)
        def cartResult2 = cartDataProvider.getCartContentsResponse(cartResponse2, [cartItemResponse2])

        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)
        def listItem1MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse1.metadata)
        def listItem2MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse2.metadata)

        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))

        when:
        def actual =  getDefaultListService.getDefaultList("1234", locationId, listItemsTransformationPipeline, ItemIncludeFields.ALL).block()

        then:
        1 * cartManager.getAllCarts(guestId, _) >> Mono.just(cartResponseList)
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(cartResponse2)
        1 * cartManager.getListCartContents(listId2,true) >> Mono.just(cartResult2)
        1 * cartManager.getListCartContents(listId,true) >> Mono.just(cartResult)

        actual.listId == cartResponse.cartId
        actual.channel == cartResponse.cartChannel
        actual.listTitle == cartResponse.tenantCartName
        actual.shortDescription == cartResponse.tenantCartDescription
        actual.listType == cartResponse.cartSubchannel
        actual.defaultList == listMetaData.defaultList

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 1
        pendingItems[0].listItemId == cartItemResponse1.cartItemId
        pendingItems[0].tcin == cartItemResponse1.tcin
        pendingItems[0].itemTitle == cartItemResponse1.shortDescription
        pendingItems[0].itemNote == cartItemResponse1.notes
        pendingItems[0].price == cartItemResponse1.price
        pendingItems[0].listPrice == cartItemResponse1.listPrice
        pendingItems[0].images == cartItemResponse1.images
        pendingItems[0].itemType == listItem1MetaData.itemType

        def completedItems = actual.completedListItems
        completedItems.size() == 1
        completedItems[0].listItemId == cartItemResponse2.cartItemId
        completedItems[0].tcin == cartItemResponse2.tcin
        completedItems[0].itemTitle == cartItemResponse2.shortDescription
        completedItems[0].itemNote == cartItemResponse2.notes
        completedItems[0].price == cartItemResponse2.price
        completedItems[0].listPrice == cartItemResponse2.listPrice
        completedItems[0].images == cartItemResponse2.images
        completedItems[0].itemType == listItem2MetaData.itemType
    }
}
