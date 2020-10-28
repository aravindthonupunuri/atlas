package com.tgt.lists.atlas.api.transport


import com.tgt.lists.cart.transport.AbandonAfterDuration
import com.tgt.lists.cart.transport.CartOption
import com.tgt.lists.cart.transport.CartResponse
import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.atlas.api.domain.CartManager
import com.tgt.lists.atlas.api.util.Constants
import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.api.util.LIST_STATUS
import com.tgt.lists.atlas.util.CartDataProvider
import com.tgt.lists.atlas.util.TestListChannel
import spock.lang.Specification

class TOMapperTest extends Specification {

    private def abandonAfterDurationInDays = 730

    def abandonAfterDuration = new AbandonAfterDuration(BigDecimal.valueOf(abandonAfterDurationInDays), AbandonAfterDuration.Type.DAYS)

    CartManager cartManager
    CartDataProvider cartDataProvider
    String guestId = "1234"

    def setup() {
        cartManager = Mock(CartManager)
        cartDataProvider = new CartDataProvider()
    }

    def "Test toCartPostRequest() integrity"() {
        given:
        ListMetaDataTO metadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        def cartMetadata = cartDataProvider.getMetaData(metadata, new UserMetaDataTO())

        def listRequest = new ListRequestTO(TestListChannel.WEB.toString(), "list1", Long.valueOf(Constants.LIST_DEFAULT_LOCATION_ID),"my favorite list", true, null, null)

        when:
        def actual = TOMapperKt.toCartPostRequest(guestId, "SHOPPING", listRequest, abandonAfterDuration, true, false)

        then:
        actual.tenantCartName == listRequest.listTitle
        actual.cartChannel == listRequest.channel.toString()
        actual.cartLocationId == listRequest.locationId.toString()
        actual.cartType == CartType.LIST.value
        actual.tenantCartDescription == listRequest.shortDescription
        actual.metadata == cartMetadata
        actual.getContentsOptions.size() == 3
        actual.getContentsOptions[0].option == CartOption.ITEMS.name()
        actual.getContentsOptions[1].option == CartOption.PRICES.name()
        actual.getContentsOptions[2].option == CartOption.PROMOTIONS.name()
    }

    def "Test toCartPostRequest() when there is no location and testMode=true"() {
        given:
        ListMetaDataTO metadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        def cartMetadata = cartDataProvider.getMetaData(metadata, new UserMetaDataTO())

        def listRequest = new ListRequestTO(TestListChannel.WEB.toString(), "list1", Long.valueOf(Constants.LIST_DEFAULT_LOCATION_ID), "my favorite list", true, null, null)

        when:
        def actual = TOMapperKt.toCartPostRequest(guestId, "SHOPPING", listRequest, abandonAfterDuration, true, true)

        then:
        actual.tenantCartName == listRequest.listTitle
        actual.cartChannel == listRequest.channel.toString()
        actual.cartLocationId == listRequest.locationId.toString()
        actual.cartType == CartType.LIST.value
        actual.tenantCartDescription == listRequest.shortDescription
        actual.metadata == cartMetadata
        actual.testCart == true
    }

    def "Test toListResponseTO() integrity"() {
        given:

        ListMetaDataTO metadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        def listId = UUID.randomUUID()
        def cartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))

        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)

        when:
        def actual = TOMapperKt.toListResponseTO(cartResponse, null, null, 0, 0, null, null)

        then:
        actual.listId == cartResponse.cartId
        actual.channel == cartResponse.cartChannel
        actual.listTitle == cartResponse.tenantCartName
        actual.shortDescription == cartResponse.tenantCartDescription
        actual.listType == cartResponse.cartSubchannel
        actual.defaultList == listMetaData.defaultList
    }

    def "Test toListResponseTO() when there is no location"() {
        given:
        ListMetaDataTO metadata = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse cartResponse = cartDataProvider.getCartResponse(UUID.randomUUID(), guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))

        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)

        when:
        def actual = TOMapperKt.toListResponseTO(cartResponse, null, null, 0, 0, null, null)

        then:
        actual.listId == cartResponse.cartId
        actual.channel == cartResponse.cartChannel
        actual.listTitle == cartResponse.tenantCartName
        actual.shortDescription == cartResponse.tenantCartDescription
        actual.listType == cartResponse.cartSubchannel
        actual.defaultList == listMetaData.defaultList
    }

    def "Test toListItemResponseTO() integrity"() {
        given:
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getItemRefId(ItemType.TCIN, tcin1)
        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(UUID.randomUUID(), UUID.randomUUID(), tenantrefId1, TestListChannel.WEB.toString(), tcin1,
            "tcin 1", 1, "some note 1", 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()), null, null, null)

        def listItemMetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse.metadata)

        when:
        def actual = TOMapperKt.toListItemResponseTO(cartItemResponse, null, 2)

        then:
        actual.listItemId == cartItemResponse.cartItemId
        actual.tcin == cartItemResponse.tcin
        actual.itemTitle == cartItemResponse.shortDescription
        actual.itemNote == cartItemResponse.notes
        actual.price == cartItemResponse.price
        actual.listPrice == cartItemResponse.listPrice
        actual.images == cartItemResponse.images
        actual.itemType == listItemMetaData.itemType
        actual.offerCount == 2
    }

    def "Test toCartItemsPostRequest integrity"() {
        given:
        def listId = UUID.randomUUID()
        def locationId = 1000L
        def tcin = "1234"
        def item = cartDataProvider.getListItemRequestTO(ItemType.TCIN, tcin, TestListChannel.MOBILE.toString())
        def itemState = LIST_ITEM_STATE.PENDING

        when:
        def cartItemsRequest = TOMapperKt.toCartItemsPostRequest(listId, locationId, item, itemState)

        then:
        cartItemsRequest != null
        cartItemsRequest.cartId == listId
        cartItemsRequest.tenantReferenceId == item.itemRefId
        cartItemsRequest.tcin == tcin
    }
}
