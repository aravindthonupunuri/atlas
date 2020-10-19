package com.tgt.lists.atlas.api.service

import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.cart.types.SearchCartsFieldGroup
import com.tgt.lists.cart.types.SearchCartsFieldGroups
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

import java.time.LocalDateTime

class GetListServiceTest extends Specification {

    GetListService getListService
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
        SortListItemsTransformationConfiguration sortListItemsTransformationConfiguration = new SortListItemsTransformationConfiguration(itemSortOrderManager)
        ListItemsTransformationPipelineConfiguration transformationPipelineConfiguration = new ListItemsTransformationPipelineConfiguration(sortListItemsTransformationConfiguration, null)
        getListService = new GetListService(cartManager, transformationPipelineConfiguration, contextContainerManager, true)
        cartDataProvider = new CartDataProvider()
    }

    def "Test getListService() integrity"() {
        given:
        UUID listId = UUID.randomUUID()
        UUID completedListId = UUID.randomUUID()

        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def tcin2 = "1235"
        def tenantrefId2 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin2)

        ListMetaDataTO metadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        ListMetaDataTO completedMetadata = new ListMetaDataTO(false, LIST_STATUS.COMPLETED)
        ListItemMetaDataTO item1MetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        ListItemMetaDataTO item2MetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)

        def cartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))
        def completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, listId.toString(),
            cartDataProvider.getMetaData(completedMetadata, new UserMetaDataTO()))

        def cartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(item1MetaData, new UserItemMetaDataTO()), null, null, null)
        def cartResult = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse1])

        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, UUID.randomUUID(), tenantrefId2, TestListChannel.WEB.toString(), tcin2,
            "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(item2MetaData, new UserItemMetaDataTO()), null, null, null)
        def completedCartResult = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1])

        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)
        def listItem1MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse1.metadata)
        def listItem2MetaData = cartDataProvider.getListItemMetaDataFromCart(completedCartItemResponse1.metadata)

        def fieldGroups = new SearchCartsFieldGroups([SearchCartsFieldGroup.CART])

        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.ALL).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse)
        1 * cartManager.getListCartContents(completedListId,true) >> Mono.just(completedCartResult)
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
        completedItems[0].listItemId == completedCartItemResponse1.cartItemId
        completedItems[0].tcin == completedCartItemResponse1.tcin
        completedItems[0].itemTitle == completedCartItemResponse1.shortDescription
        completedItems[0].itemNote == completedCartItemResponse1.notes
        completedItems[0].price == completedCartItemResponse1.price
        completedItems[0].listPrice == completedCartItemResponse1.listPrice
        completedItems[0].images == completedCartItemResponse1.images
        completedItems[0].itemType == listItem2MetaData.itemType
    }

    def "Test getListService() when getting completed cart contents fails when include items is ALL with 1 pending TCIN item"() {
        given:
        UUID listId = UUID.randomUUID()
        UUID completedListId = UUID.randomUUID()

        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)

        ListMetaDataTO metadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        ListMetaDataTO completedMetadata = new ListMetaDataTO(false, LIST_STATUS.COMPLETED)
        ListItemMetaDataTO item1MetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)

        def cartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))
        def completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, listId.toString(),
            cartDataProvider.getMetaData(completedMetadata, new UserMetaDataTO()))

        def cartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(item1MetaData, new UserItemMetaDataTO()), null, null, null)
        def cartResult = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse1])
        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)
        def listItem1MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse1.metadata)

        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.ALL).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse)
        1 * cartManager.getListCartContents(completedListId,true) >> Mono.error(new RuntimeException("some exception"))
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

        actual.completedListItems.size() == 0
    }

    def "Test getListService() when getting pending cart contents fails when include items is ALL"() {
        given:
        UUID listId = UUID.randomUUID()
        UUID completedListId = UUID.randomUUID()

        def tcin1 = "1235"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)

        ListMetaDataTO completedMetadata = new ListMetaDataTO(false, LIST_STATUS.COMPLETED)
        ListItemMetaDataTO item2MetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)

        def completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, listId.toString(),
            cartDataProvider.getMetaData(completedMetadata, new UserMetaDataTO()))

        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, UUID.randomUUID(), tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(item2MetaData, new UserItemMetaDataTO()), null, null, null)
        def completedCartResult = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1])

        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))

        when:
        getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.ALL).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse)
        1 * cartManager.getListCartContents(completedListId,true) >> Mono.just(completedCartResult)
        1 * cartManager.getListCartContents(listId,true) >> Mono.error(new RuntimeException("some exception"))
        thrown(RuntimeException)
    }

    def "Test getListService() when searching completed cart fails when include items is COMPLETED"() {
        given:
        UUID listId = UUID.randomUUID()
        def tcin1 = "1235"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)

        ListMetaDataTO metadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        ListMetaDataTO completedMetadata = new ListMetaDataTO(false, LIST_STATUS.COMPLETED)
        ListItemMetaDataTO item1MetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)

        def cartResponse = cartDataProvider.getCartResponse(listId, guestId,
                TestListChannel.WEB.toString(), CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))

        def cartItemResponse1 = cartDataProvider.getCartItemResponseForGenericItems(listId, UUID.randomUUID(), tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(item1MetaData, new UserItemMetaDataTO()), null, null, null)
        def cartResult = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse1])

        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.COMPLETED).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.empty()
        1 * cartManager.getListCartContents(listId,true) >> Mono.just(cartResult)
        actual.completedListItems.isEmpty()
    }

    def "Test getListService() when getting completed cart contents fails when include items is COMPLETED"() {
        given:
        UUID listId = UUID.randomUUID()
        UUID completedListId = UUID.randomUUID()
        def tcin1 = "1235"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)

        ListMetaDataTO metadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        ListMetaDataTO completedMetadata = new ListMetaDataTO(false, LIST_STATUS.COMPLETED)
        ListItemMetaDataTO item1MetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)

        def cartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))
        def completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, listId.toString(),
            cartDataProvider.getMetaData(completedMetadata, new UserMetaDataTO()))

        def cartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(item1MetaData, new UserItemMetaDataTO()), null, null, null)
        def cartResult = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse1])

        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.COMPLETED).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse)
        1 * cartManager.getListCartContents(completedListId,true) >> Mono.error(new RuntimeException("some exception"))
        1 * cartManager.getListCartContents(listId,true) >> Mono.just(cartResult)
        actual.completedListItems.isEmpty()
    }

    def "Test getListService() when getting pending cart contents fails when include items is COMPLETED"() {
        given:
        UUID listId = UUID.randomUUID()
        UUID completedListId = UUID.randomUUID()
        def tcin1 = "1235"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)

        ListMetaDataTO completedMetadata = new ListMetaDataTO(false, LIST_STATUS.COMPLETED)
        ListItemMetaDataTO item2MetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)

        def completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, listId.toString(),
            cartDataProvider.getMetaData(completedMetadata, new UserMetaDataTO()))

        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, UUID.randomUUID(), tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(item2MetaData, new UserItemMetaDataTO()), null, null, null)
        def completedCartResult = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1])

        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))

        when:
        getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.COMPLETED).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse)
        1 * cartManager.getListCartContents(completedListId,true) >> Mono.just(completedCartResult)
        1 * cartManager.getListCartContents(listId,true) >> Mono.error(new RuntimeException("some exception"))
        thrown(RuntimeException)
    }

    def "Test getListService() when there is no pending items"() {
        given:
        UUID listId = UUID.randomUUID()
        UUID completedListId = UUID.randomUUID()
        def tcin1 = "1235"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)

        ListMetaDataTO metadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        ListMetaDataTO completedMetadata = new ListMetaDataTO(false, LIST_STATUS.COMPLETED)
        ListItemMetaDataTO item2MetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)

        def cartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))
        def completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, listId.toString(),
            cartDataProvider.getMetaData(completedMetadata, new UserMetaDataTO()))

        def cartResult = cartDataProvider.getCartContentsResponse(cartResponse, null)

        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, UUID.randomUUID(), tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(item2MetaData, new UserItemMetaDataTO()), null, null, null)
        def completedCartResult = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1])

        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)
        def listItem2MetaData = cartDataProvider.getListItemMetaDataFromCart(completedCartItemResponse1.metadata)

        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.ALL).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse)
        1 * cartManager.getListCartContents(completedListId,true) >> Mono.just(completedCartResult)
        1 * cartManager.getListCartContents(listId,true) >> Mono.just(cartResult)

        actual.listId == cartResponse.cartId
        actual.channel == cartResponse.cartChannel
        actual.listTitle == cartResponse.tenantCartName
        actual.shortDescription == cartResponse.tenantCartDescription
        actual.listType == cartResponse.cartSubchannel
        actual.defaultList == listMetaData.defaultList

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 0

        def completedItems = actual.completedListItems
        completedItems.size() == 1
        completedItems[0].listItemId == completedCartItemResponse1.cartItemId
        completedItems[0].tcin == completedCartItemResponse1.tcin
        completedItems[0].itemTitle == completedCartItemResponse1.shortDescription
        completedItems[0].itemNote == completedCartItemResponse1.notes
        completedItems[0].price == completedCartItemResponse1.price
        completedItems[0].listPrice == completedCartItemResponse1.listPrice
        completedItems[0].images == completedCartItemResponse1.images
        completedItems[0].itemType == listItem2MetaData.itemType
    }

    def "Test getListService() when there is no completed items with 1 pending TCIN item"() {
        given:
        UUID listId = UUID.randomUUID()
        UUID completedListId = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)

        ListMetaDataTO metadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        ListMetaDataTO completedMetadata = new ListMetaDataTO(false, LIST_STATUS.COMPLETED)
        ListItemMetaDataTO item1MetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)

        def cartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))
        def completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, listId.toString(),
            cartDataProvider.getMetaData(completedMetadata, new UserMetaDataTO()))

        def cartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(item1MetaData, new UserItemMetaDataTO()), null, null, null)
        def cartResult = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse1])

        def completedCartResult = cartDataProvider.getCartContentsResponse(completedCartResponse, null)

        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)
        def listItem1MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse1.metadata)

        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.ALL).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse)
        1 * cartManager.getListCartContents(completedListId,true) >> Mono.just(completedCartResult)
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

        actual.completedListItems.size() == 0
    }

    def "Test getListService() integrity when item include field is PENDING with 1 pending TCIN item"() {
        given:
        UUID listId = UUID.randomUUID()

        ListMetaDataTO metadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        ListItemMetaDataTO item1MetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def tcin1 = "1235"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)

        def cartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))

        def cartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(item1MetaData, new UserItemMetaDataTO()), null, null, null)
        def cartResult = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse1])

        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)
        def listItem1MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse1.metadata)

        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.PENDING).block()

        then:
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

        actual.completedListItems.size() == 0
    }

    def "Test getListService() when item include field is COMPLETED"() {
        given:
        UUID listId = UUID.randomUUID()
        UUID completedListId = UUID.randomUUID()
        def tcin1 = "1235"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)

        ListMetaDataTO metadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        ListMetaDataTO completedMetadata = new ListMetaDataTO(false, LIST_STATUS.COMPLETED)
        ListItemMetaDataTO item2MetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)

        def cartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))
        def completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, listId.toString(),
            cartDataProvider.getMetaData(completedMetadata, new UserMetaDataTO()))
        def cartResult = cartDataProvider.getCartContentsResponse(cartResponse, null)

        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, UUID.randomUUID(), tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(item2MetaData, new UserItemMetaDataTO()), null, null, null)
        def completedCartResult = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1])

        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)
        def listItem2MetaData = cartDataProvider.getListItemMetaDataFromCart(completedCartItemResponse1.metadata)

        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.COMPLETED).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse)
        1 * cartManager.getListCartContents(completedListId,true) >> Mono.just(completedCartResult)
        1 * cartManager.getListCartContents(listId,true) >> Mono.just(cartResult)

        actual.listId == cartResponse.cartId
        actual.channel == cartResponse.cartChannel
        actual.listTitle == cartResponse.tenantCartName
        actual.shortDescription == cartResponse.tenantCartDescription
        actual.listType == cartResponse.cartSubchannel
        actual.defaultList == listMetaData.defaultList

        actual.pendingListItems.size() == 0

        def completedItems = actual.completedListItems
        completedItems.size() == 1
        completedItems[0].listItemId == completedCartItemResponse1.cartItemId
        completedItems[0].tcin == completedCartItemResponse1.tcin
        completedItems[0].itemTitle == completedCartItemResponse1.shortDescription
        completedItems[0].itemNote == completedCartItemResponse1.notes
        completedItems[0].price == completedCartItemResponse1.price
        completedItems[0].listPrice == completedCartItemResponse1.listPrice
        completedItems[0].images == completedCartItemResponse1.images
        completedItems[0].itemType == listItem2MetaData.itemType
    }

    def "Test getListService() when include only pending items with 1 pending TCIN item"() {
        given:
        def listId = UUID.randomUUID()
        ListMetaDataTO metadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)

        def cartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))
        def cartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)
        def cartResult = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse1])
        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)
        def listItem1MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse1.metadata)

        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.PENDING).block()

        then:
        1 * cartManager.getListCartContents(_,true) >> Mono.just(cartResult)

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
    }

    def "Test getListService() when include only completed items"() {
        given:
        ListMetaDataTO metadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        ListMetaDataTO completedMetadata = new ListMetaDataTO(true, LIST_STATUS.COMPLETED)
        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def tcin1 = "1235"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)

        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def cartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))
        def completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, listId.toString(),
            cartDataProvider.getMetaData(completedMetadata, new UserMetaDataTO()))

        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(completedListId, UUID.randomUUID(), tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            "tcin 2", 1, "some note 2", 20, 20, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)
        def cartResult = cartDataProvider.getCartContentsResponse(cartResponse, null)
        def completedCartResult = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1])

        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)
        def listItem1MetaData = cartDataProvider.getListItemMetaDataFromCart(completedCartItemResponse1.metadata)

        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.ALL).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse)
        1 * cartManager.getListCartContents(listId,true) >> Mono.just(cartResult)
        1 * cartManager.getListCartContents(completedListId,true) >> Mono.just(completedCartResult)

        actual.listId == cartResponse.cartId
        actual.channel == cartResponse.cartChannel
        actual.listTitle == cartResponse.tenantCartName
        actual.shortDescription == cartResponse.tenantCartDescription
        actual.listType == cartResponse.cartSubchannel
        actual.defaultList == listMetaData.defaultList

        def completedItems = actual.completedListItems
        completedItems.size() == 1
        completedItems[0].listItemId == completedCartItemResponse1.cartItemId
        completedItems[0].tcin == completedCartItemResponse1.tcin
        completedItems[0].itemTitle == completedCartItemResponse1.shortDescription
        completedItems[0].itemNote == completedCartItemResponse1.notes
        completedItems[0].price == completedCartItemResponse1.price
        completedItems[0].listPrice == completedCartItemResponse1.listPrice
        completedItems[0].images == completedCartItemResponse1.images
        completedItems[0].itemType == listItem1MetaData.itemType
    }

    def "Test getListService() item and sorting based on title and descending order with 1 TCIN item and 1 GENERIC item in pending list"() {
        given:
        ListMetaDataTO metadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.GENERIC_ITEM, LIST_ITEM_STATE.PENDING)
        def tcin1 = "1235"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def gItem1 = "item 2"
        def gTenantRefItemId1 = cartDataProvider.getTenantRefId(ItemType.GENERIC_ITEM, gItem1)
        def listId = UUID.randomUUID()

        def cartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))
        def cartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), tcin1, TestListChannel.WEB.toString(), tenantrefId1,
            "item 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)
        def cartItemResponse2 = cartDataProvider.getCartItemResponseForGenericItems(listId, UUID.randomUUID(), gTenantRefItemId1, TestListChannel.WEB.toString(), null,
            gItem1, 1, "some note 2", 20, 20, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)

        def cartResult = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse1, cartItemResponse2])
        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)
        def listItem1MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse1.metadata)
        def listItem2MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse2.metadata)

        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.DESCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.ALL).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.empty()
        1 * cartManager.getListCartContents(listId,true) >> Mono.just(cartResult)

        actual.listId == cartResponse.cartId
        actual.channel == cartResponse.cartChannel
        actual.listTitle == cartResponse.tenantCartName
        actual.shortDescription == cartResponse.tenantCartDescription
        actual.listType == cartResponse.cartSubchannel
        actual.defaultList == listMetaData.defaultList

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 2

        pendingItems[0].listItemId == cartItemResponse2.cartItemId
        pendingItems[0].tcin == cartItemResponse2.tcin
        pendingItems[0].itemTitle == cartItemResponse2.tenantItemName
        pendingItems[0].itemNote == cartItemResponse2.notes
        pendingItems[0].price == cartItemResponse2.price
        pendingItems[0].listPrice == cartItemResponse2.listPrice
        pendingItems[0].images == cartItemResponse2.images
        pendingItems[0].itemType == listItem2MetaData.itemType


        pendingItems[1].listItemId == cartItemResponse1.cartItemId
        pendingItems[1].tcin == cartItemResponse1.tcin
        pendingItems[1].itemTitle == cartItemResponse1.shortDescription
        pendingItems[1].itemNote == cartItemResponse1.notes
        pendingItems[1].price == cartItemResponse1.price
        pendingItems[1].listPrice == cartItemResponse1.listPrice
        pendingItems[1].images == cartItemResponse1.images
        pendingItems[1].itemType == listItem1MetaData.itemType
    }

    def "Test getListService() sorting based on itemCreatedDate with Ascending order"() {
        given:
        ListMetaDataTO metadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.GENERIC_ITEM, LIST_ITEM_STATE.PENDING)
        def tcin1 = "1235"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def gItem1 = "item 2"
        def gTenantRefItemId1 = cartDataProvider.getTenantRefId(ItemType.GENERIC_ITEM, gItem1)

        def listId = UUID.randomUUID()
        def cartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))
        def cartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, LocalDateTime.now(), null)
        def cartItemResponse2 = cartDataProvider.getCartItemResponseForGenericItems(listId, UUID.randomUUID(), gTenantRefItemId1, TestListChannel.WEB.toString(), null,
            gItem1, 1, "some note 2", 20, 20, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, LocalDateTime.now().minusDays(1), null)

        def cartResult = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse1, cartItemResponse2])
        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)
        def listItem1MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse1.metadata)
        def listItem2MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse2.metadata)

        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.ALL).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.empty()
        1 * cartManager.getListCartContents(listId,true) >> Mono.just(cartResult)

        actual.listId == cartResponse.cartId
        actual.channel == cartResponse.cartChannel
        actual.listTitle == cartResponse.tenantCartName
        actual.shortDescription == cartResponse.tenantCartDescription
        actual.listType == cartResponse.cartSubchannel
        actual.defaultList == listMetaData.defaultList

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 2

        pendingItems[0].listItemId == cartItemResponse2.cartItemId
        pendingItems[0].tcin == cartItemResponse2.tcin
        pendingItems[0].itemTitle == cartItemResponse2.tenantItemName
        pendingItems[0].itemNote == cartItemResponse2.notes
        pendingItems[0].price == cartItemResponse2.price
        pendingItems[0].listPrice == cartItemResponse2.listPrice
        pendingItems[0].images == cartItemResponse2.images
        pendingItems[0].itemType == listItem2MetaData.itemType

        pendingItems[1].listItemId == cartItemResponse1.cartItemId
        pendingItems[1].tcin == cartItemResponse1.tcin
        pendingItems[1].itemTitle == cartItemResponse1.shortDescription
        pendingItems[1].itemNote == cartItemResponse1.notes
        pendingItems[1].price == cartItemResponse1.price
        pendingItems[1].listPrice == cartItemResponse1.listPrice
        pendingItems[1].images == cartItemResponse1.images
        pendingItems[1].itemType == listItem1MetaData.itemType

    }

    def "Test getListService() sorting based on itemUpdated with Ascending order"() {
        given:
        ListMetaDataTO metadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.GENERIC_ITEM, LIST_ITEM_STATE.PENDING)
        def listId = UUID.randomUUID()
        def tcin1 = "1235"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def gItem1 = "item 2"
        def gTenantRefItemId1 = cartDataProvider.getTenantRefId(ItemType.GENERIC_ITEM, gItem1)

        def cartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))
        def cartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            "tcin 1", 1, "note", 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, LocalDateTime.now())
        def cartItemResponse2 = cartDataProvider.getCartItemResponseForGenericItems(listId, UUID.randomUUID(), gTenantRefItemId1, TestListChannel.WEB.toString(), null,
            gItem1, 1, "some note 2", 20, 20, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, LocalDateTime.now().minusDays(1))

        def cartResult = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse1, cartItemResponse2])
        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)
        def listItem1MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse1.metadata)
        def listItem2MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse2.metadata)

        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.ASCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.ALL).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.empty()
        1 * cartManager.getListCartContents(listId,true) >> Mono.just(cartResult)

        actual.listId == cartResponse.cartId
        actual.channel == cartResponse.cartChannel
        actual.listTitle == cartResponse.tenantCartName
        actual.shortDescription == cartResponse.tenantCartDescription
        actual.listType == cartResponse.cartSubchannel
        actual.defaultList == listMetaData.defaultList

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 2

        pendingItems[0].listItemId == cartItemResponse2.cartItemId
        pendingItems[0].tcin == cartItemResponse2.tcin
        pendingItems[0].itemTitle == cartItemResponse2.tenantItemName
        pendingItems[0].itemNote == cartItemResponse2.notes
        pendingItems[0].price == cartItemResponse2.price
        pendingItems[0].listPrice == cartItemResponse2.listPrice
        pendingItems[0].images == cartItemResponse2.images
        pendingItems[0].itemType == listItem2MetaData.itemType

        pendingItems[1].listItemId == cartItemResponse1.cartItemId
        pendingItems[1].tcin == cartItemResponse1.tcin
        pendingItems[1].itemTitle == cartItemResponse1.shortDescription
        pendingItems[1].itemNote == cartItemResponse1.notes
        pendingItems[1].price == cartItemResponse1.price
        pendingItems[1].listPrice == cartItemResponse1.listPrice
        pendingItems[1].images == cartItemResponse1.images
        pendingItems[1].itemType == listItem1MetaData.itemType
    }

    def "Test getListService() item sorting based on itemPosition with guest preferred Sort order"() {
        given:
        ListMetaDataTO metadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.GENERIC_ITEM, LIST_ITEM_STATE.PENDING)
        def tcin1 = "1235"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def gItem1 = "item 2"
        def gTenantRefItemId1 = cartDataProvider.getTenantRefId(ItemType.GENERIC_ITEM, gItem1)

        def listId = UUID.randomUUID()
        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, TestListChannel.WEB.toString(), CartType.LIST,
            "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))
        def cartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "PENDING",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)
        def cartItemResponse2 = cartDataProvider.getCartItemResponseForGenericItems(listId, UUID.randomUUID(), gTenantRefItemId1, TestListChannel.WEB.toString(), null,
            gItem1, 1, "some note 2", 20, 20, "Stand Alone", "PENDING",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)

        def cartResult = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse1, cartItemResponse2])

        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)
        def listResponse = new com.tgt.lists.atlas.api.domain.model.List(listId, cartItemResponse1.cartItemId.toString() + "," + cartItemResponse2.cartItemId.toString(),
            null, null)

        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.DESCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.ALL).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.empty()
        1 * cartManager.getListCartContents(listId,true) >> Mono.just(cartResult)
        0 * listRepository.find(_) >> Mono.just(listResponse)

        actual.listId == cartResponse.cartId
        actual.channel == cartResponse.cartChannel
        actual.listTitle == cartResponse.tenantCartName
        actual.shortDescription == cartResponse.tenantCartDescription
        actual.listType == cartResponse.cartSubchannel
        actual.defaultList == listMetaData.defaultList

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 2
        pendingItems[0].listItemId == cartItemResponse1.cartItemId
        pendingItems[1].listItemId == cartItemResponse2.cartItemId
    }

    def "Test getListService() item sorting based on itemPosition with guest preferred Sort order for empty list"() {
        given:
        ListMetaDataTO metadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.GENERIC_ITEM, LIST_ITEM_STATE.PENDING)
        def tcin1 = "1235"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def gItem1 = "item 2"
        def gTenantRefItemId1 = cartDataProvider.getTenantRefId(ItemType.GENERIC_ITEM, gItem1)

        def listId = UUID.randomUUID()
        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, TestListChannel.WEB.toString(), CartType.LIST,
            "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))
        def cartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "PENDING",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)
        def cartItemResponse2 = cartDataProvider.getCartItemResponseForGenericItems(listId, UUID.randomUUID(), gTenantRefItemId1, TestListChannel.WEB.toString(), null,
            gItem1, 1, "some note 2", 20, 20, "Stand Alone", "PENDING",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)

        def cartResult = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse1, cartItemResponse2])
        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)
        def listResponse = new com.tgt.lists.atlas.api.domain.model.List(listId, ",,", null, null)

        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.DESCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.ALL).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.empty()
        1 * cartManager.getListCartContents(listId,true) >> Mono.just(cartResult)

        0 * listRepository.find(_) >> Mono.just(listResponse)

        actual.listId == cartResponse.cartId
        actual.channel == cartResponse.cartChannel
        actual.listTitle == cartResponse.tenantCartName
        actual.shortDescription == cartResponse.tenantCartDescription
        actual.listType == cartResponse.cartSubchannel
        actual.defaultList == listMetaData.defaultList

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 2
        pendingItems[0].listItemId == cartItemResponse1.cartItemId
        pendingItems[1].listItemId == cartItemResponse2.cartItemId
    }

    def "Test getListService() item sorting based on itemPosition when items are missing in SortOrder but present in Pending Items"() {
        given:
        ListMetaDataTO metadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.GENERIC_ITEM, LIST_ITEM_STATE.PENDING)
        def tcin1 = "1235"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def gItem1 = "item 2"
        def gTenantRefItemId1 = cartDataProvider.getTenantRefId(ItemType.GENERIC_ITEM, gItem1)

        def listId = UUID.randomUUID()
        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, TestListChannel.WEB.toString(), CartType.LIST,
            "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))
        def cartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "PENDING",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)
        def cartItemResponse2 = cartDataProvider.getCartItemResponseForGenericItems(listId, UUID.randomUUID(), gTenantRefItemId1, TestListChannel.WEB.toString(), null,
            gItem1, 1, "some note 2", 20, 20, "Stand Alone", "PENDING",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)

        def cartResult = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse1, cartItemResponse2])
        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)
        def listResponse = new com.tgt.lists.atlas.api.domain.model.List(listId, cartItemResponse1.cartItemId.toString(), null, null)

        ListItemsTransformationPipeline listItemsTransformationPipeline = new ListItemsTransformationPipeline()
        listItemsTransformationPipeline.addStep(new SortListItemsTransformationStep(ItemSortFieldGroup.ITEM_TITLE, ItemSortOrderGroup.DESCENDING))

        when:
        def actual = getListService.getList(guestId, locationId, listId, listItemsTransformationPipeline, ItemIncludeFields.ALL).block()

        then:
        1 * cartManager.getCompletedListCart(listId) >> Mono.empty()
        1 * cartManager.getListCartContents(listId,true) >> Mono.just(cartResult)
        0 * listRepository.find(_) >> Mono.just(listResponse)

        actual.listId == cartResponse.cartId
        actual.channel == cartResponse.cartChannel
        actual.listTitle == cartResponse.tenantCartName
        actual.shortDescription == cartResponse.tenantCartDescription
        actual.listType == cartResponse.cartSubchannel
        actual.defaultList == listMetaData.defaultList

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 2
        pendingItems[0].listItemId == cartItemResponse1.cartItemId
        pendingItems[1].listItemId == cartItemResponse2.cartItemId
    }
}
