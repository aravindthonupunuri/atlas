package com.tgt.lists.atlas.api.service


import com.tgt.lists.atlas.api.domain.CartManager
import com.tgt.lists.atlas.api.domain.ContextContainerManager
import com.tgt.lists.atlas.api.transport.ListItemMetaDataTO
import com.tgt.lists.atlas.api.transport.UserItemMetaDataTO
import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.util.CartDataProvider
import com.tgt.lists.atlas.util.TestListChannel
import reactor.core.publisher.Mono
import spock.lang.Specification

class GetListItemServiceTest extends Specification {

    GetListItemService getListItemService
    CartDataProvider cartDataProvider
    ContextContainerManager contextContainerManager
    CartManager cartManager

    String guestId = "1234"
    Long locationId= 1375

    def setup() {
        contextContainerManager = new ContextContainerManager()
        cartManager = Mock(CartManager)
        getListItemService = new GetListItemService(cartManager, true)
        cartDataProvider = new CartDataProvider()
    }

    def "test getListItemService() integrity"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)

        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, listItemId, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            null, 1, "test item", 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()), null, null, null)
        def listItemMetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse.metadata)

        when:
        def actual = getListItemService.getListItem(guestId, locationId, listId, listItemId).block()

        then:
        1 * cartManager.getCartItem(_ ,_) >> Mono.just(cartItemResponse)

        actual.listItemId == cartItemResponse.cartItemId
        actual.tcin == cartItemResponse.tcin
        actual.itemTitle == cartItemResponse.tenantItemName
        actual.itemNote == cartItemResponse.notes
        actual.price == cartItemResponse.price
        actual.listPrice == cartItemResponse.listPrice
        actual.images == cartItemResponse.images
        actual.itemType == listItemMetaData.itemType
    }

    def "test getListItemService() when there is no placements"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)

        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, listItemId, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                null, 1, "test item", 10, 10, "Stand Alone", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()), null, null, null)
        def listItemMetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse.metadata)


        when:
        def actual = getListItemService.getListItem(guestId, locationId, listId, listItemId).block()

        then:
        1 * cartManager.getCartItem(_ ,_) >> Mono.just(cartItemResponse)

        actual.listItemId == cartItemResponse.cartItemId
        actual.tcin == cartItemResponse.tcin
        actual.itemTitle == cartItemResponse.tenantItemName
        actual.itemNote == cartItemResponse.notes
        actual.price == cartItemResponse.price
        actual.listPrice == cartItemResponse.listPrice
        actual.images == cartItemResponse.images
        actual.itemType == listItemMetaData.itemType
    }

    def "test getListItemService() with store in placements call error"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)

        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, listItemId, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            null, 1, "test item", 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()), null, null, null)
        def listItemMetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse.metadata)

        when:
        def actual = getListItemService.getListItem(guestId, locationId, listId, listItemId).block()

        then:
        1 * cartManager.getCartItem(_ ,_) >> Mono.just(cartItemResponse)

        actual.listItemId == cartItemResponse.cartItemId
        actual.tcin == cartItemResponse.tcin
        actual.itemTitle == cartItemResponse.tenantItemName
        actual.itemNote == cartItemResponse.notes
        actual.price == cartItemResponse.price
        actual.listPrice == cartItemResponse.listPrice
        actual.images == cartItemResponse.images
        actual.itemType == listItemMetaData.itemType
    }

    def "test getListItemService() with a parent item"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)

        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, listItemId, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            null, 1, "test item", 10, 10, "Variation Parent w/in Collection", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()), null, null, null)
        def listItemMetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse.metadata)

        when:
        def actual = getListItemService.getListItem(guestId, locationId, listId, listItemId).block()

        then:
        1 * cartManager.getCartItem(_ ,_) >> Mono.just(cartItemResponse)

        actual.listItemId == cartItemResponse.cartItemId
        actual.tcin == cartItemResponse.tcin
        actual.itemTitle == cartItemResponse.tenantItemName
        actual.itemNote == cartItemResponse.notes
        actual.price == cartItemResponse.price
        actual.listPrice == cartItemResponse.listPrice
        actual.images == cartItemResponse.images
        actual.itemType == listItemMetaData.itemType
    }

    def "test getListItemService() for getting an item from completed_list_items"() {
        given:
        def listId = UUID.randomUUID()
        def listItemId = UUID.randomUUID()
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)

        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, listItemId, tenantrefId1, TestListChannel.WEB.toString(), tcin1,
                null, 1, "test item", 10, 10, "Variation Parent w/in Collection", "READY",
                "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()), null, null, null)
        def listItemMetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse.metadata)

        when:
        def actual = getListItemService.getListItem(guestId, locationId, listId, listItemId).block()

        then:
        1 * cartManager.getCartItem(listId, _) >> Mono.empty()
        1 * cartManager.getItemInCompletedCart(listId, listItemId) >> Mono.just(cartItemResponse)

        actual.listItemId == cartItemResponse.cartItemId
        actual.tcin == cartItemResponse.tcin
        actual.itemTitle == cartItemResponse.tenantItemName
        actual.itemNote == cartItemResponse.notes
        actual.price == cartItemResponse.price
        actual.listPrice == cartItemResponse.listPrice
        actual.images == cartItemResponse.images
        actual.itemType == listItemMetaData.itemType
    }
}
