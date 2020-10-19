package com.tgt.lists.atlas.api.util

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tgt.lists.atlas.api.transport.ListItemMetaDataTO
import com.tgt.lists.atlas.api.transport.ListMetaDataTO
import com.tgt.lists.atlas.api.transport.UserItemMetaDataTO
import com.tgt.lists.atlas.api.transport.UserMetaDataTO

val mapper = jacksonObjectMapper()

fun getListMetaDataFromCart(cartMetadata: Map<String, Any>?): ListMetaDataTO {
    var metadata: ListMetaDataTO? = mapper.readValue<ListMetaDataTO>((cartMetadata?.get(Constants.LIST_METADATA) as? String).toString())
    if (metadata == null) {
        metadata = ListMetaDataTO(listStatus = null)
    }
    return metadata
}

fun getUserMetaDataFromCart(cartMetadata: Map<String, Any>?): UserMetaDataTO? {
    var metadata: UserMetaDataTO? = mapper.readValue<UserMetaDataTO>((cartMetadata?.get(Constants.USER_METADATA) as? String).toString())
    if (metadata == null) {
        metadata = UserMetaDataTO()
    }
    return metadata
}

fun setCartMetaDataFromList(
    defaultList: Boolean = false,
    listStatus: LIST_STATUS? = LIST_STATUS.PENDING,
    tenantMetaData: Map<String, Any>? = null
): MutableMap<String, Any> {
    val metadata = mutableMapOf<String, Any>()

    // Push un-mapped list to cart attributes into cart meta data
    val listMetaData = ListMetaDataTO(
        defaultList = defaultList,
        listStatus = listStatus ?: LIST_STATUS.PENDING)

    val tenantUserMetaData = UserMetaDataTO(
        userMetaData = tenantMetaData
    )

    metadata[Constants.LIST_METADATA] = mapper.writeValueAsString(listMetaData)
    metadata[Constants.USER_METADATA] = mapper.writeValueAsString(tenantUserMetaData)
    return metadata
}

fun setCartUserMetaDataFromList(tenantUserMetaData: Map<String, Any>? = null): MutableMap<String, Any> {
    val metadata = mutableMapOf<String, Any>()

    val tenantUserMetaDataTO = UserMetaDataTO(
            userMetaData = tenantUserMetaData
    )

    metadata[Constants.USER_METADATA] = mapper.writeValueAsString(tenantUserMetaDataTO)
    return metadata
}

fun getListItemMetaDataFromCart(cartItemMetadata: Map<String, Any>?): ListItemMetaDataTO? {
    return mapper.readValue<ListItemMetaDataTO>((cartItemMetadata?.get(Constants.LIST_ITEM_METADATA) as? String).toString())
}

fun getUserItemMetaDataFromCart(cartItemMetadata: Map<String, Any>?): UserItemMetaDataTO? {
    return mapper.readValue<UserItemMetaDataTO>((cartItemMetadata?.get(Constants.USER_ITEM_METADATA) as? String).toString())
}

fun setCartItemMetaDataForListItem(
    itemType: ItemType?,
    itemState: LIST_ITEM_STATE?,
    tenantItemMetaData: Map<String, Any>?
): MutableMap<String, Any> {
    val metadata = mutableMapOf<String, Any>()

    val listItemMetaData = ListItemMetaDataTO(
        itemType = itemType,
        itemState = itemState
    )

    val tenantMetaData = UserItemMetaDataTO(
        userMetaData = tenantItemMetaData
    )

    metadata[Constants.LIST_ITEM_METADATA] = mapper.writeValueAsString(listItemMetaData)
    metadata[Constants.USER_ITEM_METADATA] = mapper.writeValueAsString(tenantMetaData)
    return metadata
}

fun setCartUserItemMetaDataForListItem(tenantUserItemMetaData: Map<String, Any>?): MutableMap<String, Any> {
    val metadata = mutableMapOf<String, Any>()

    val tenantMetaData = UserItemMetaDataTO(
            userMetaData = tenantUserItemMetaData
    )

    metadata[Constants.USER_ITEM_METADATA] = mapper.writeValueAsString(tenantMetaData)
    return metadata
}
