package com.tgt.lists.atlas.api.transport

import com.tgt.lists.atlas.api.util.*
import com.tgt.lists.atlas.api.util.AppErrorCodes.RESPONSE_BODY_VIOLATION_ERROR_CODE
import com.tgt.lists.cart.transport.*
import com.tgt.lists.common.components.exception.InternalServerException
import com.tgt.lists.atlas.api.util.Constants.LIST_DEFAULT_LOCATION_ID
import java.time.LocalDateTime
import java.util.*
import javax.validation.Validation

fun toCartPostRequest(
    guestId: String,
    listType: String,
    listRequest: ListRequestTO,
    abandonAfterDuration: AbandonAfterDuration,
    defaultList: Boolean,
    testMode: Boolean
): CartPostRequest {
    val metadata = setCartMetaDataFromList(
        defaultList = defaultList,
        tenantMetaData = listRequest.metadata)

    return CartPostRequest(
        cartType = CartType.LIST.value,
        cartNumber = UUID.randomUUID().toString(), // cart needs this to be random. For now setting uuid.
        guestId = guestId,
        cartSubchannel = listType,
        tenantCartName = listRequest.listTitle,
        cartChannel = listRequest.channel,
        tenantCartDescription = listRequest.shortDescription,
        cartLocationId = listRequest.locationId?.toString() ?: LIST_DEFAULT_LOCATION_ID,
        agentId = listRequest.agentId,
        abandonAfterDuration = abandonAfterDuration,
        metadata = metadata,
        testCart = testMode,
        addItemOptions = arrayOf(AddItemOptions(option = CartOption.ITEMS.name, enabled = true, stopOnFailure = false),
                AddItemOptions(option = CartOption.PRICES.name, enabled = true, stopOnFailure = false),
                AddItemOptions(option = CartOption.PROMOTIONS.name, enabled = true, stopOnFailure = false)),
        getContentsOptions = arrayOf(GetContentsOptions(option = CartOption.ITEMS.name, enabled = true, stopOnFailure = false),
            GetContentsOptions(option = CartOption.PRICES.name, enabled = true, stopOnFailure = false),
            GetContentsOptions(option = CartOption.PROMOTIONS.name, enabled = true, stopOnFailure = false))
    )
}

fun toListResponseTO(
    cartResponse: CartResponse,
    pendingListItems: List<ListItemResponseTO>? = null,
    completedListItems: List<ListItemResponseTO>? = null,
    maxPendingItemCount: Int? = 0,
    maxCompletedItemsCount: Int? = 0,
    maxPendingPageCount: Int? = 0,
    maxCompletedPageCount: Int? = 0
): ListResponseTO {
    val cartMetadata = cartResponse.metadata

    val listMetadata = getListMetaDataFromCart(cartMetadata)
    val userMetadata = getUserMetaDataFromCart(cartMetadata)

    return ListResponseTO(
        listId = cartResponse.cartId,
        channel = cartResponse.cartChannel,
        listType = cartResponse.cartSubchannel,
        defaultList = listMetadata.defaultList,
        listTitle = cartResponse.tenantCartName,
        shortDescription = cartResponse.tenantCartDescription,
        agentId = cartResponse.agentId,
        addedTs = cartResponse.createdAt.let { addZ(it) },
        lastModifiedTs = cartResponse.updatedAt.let { addZ(it) },
        metadata = userMetadata?.userMetaData,
        pendingListItems = pendingListItems,
        completedListItems = completedListItems,
        maxPendingItemsCount = maxPendingItemCount,
        maxCompletedItemsCount = maxCompletedItemsCount,
        maxPendingPageCount = maxPendingPageCount,
        maxCompletedPageCount = maxCompletedPageCount
    )
}

fun toCartItemsPostRequest(listId: UUID, locationId: Long, listItemRequest: ListItemRequestTO, itemState: LIST_ITEM_STATE): CartItemsRequest {
    val metadata = setCartItemMetaDataForListItem(
        itemType = listItemRequest.itemType,
        itemState = itemState,
        tenantItemMetaData = listItemRequest.metadata)

    return CartItemsRequest(
        cartId = listId,
        itemAddChannel = listItemRequest.channel?.let { listItemRequest.channel.toString() },
        locationId = locationId.toString(),
        tcin = listItemRequest.tcin,
        notes = listItemRequest.itemNote,
        tenantItemName = listItemRequest.itemTitle,
        tenantReferenceId = listItemRequest.itemRefId,
        metadata = metadata,
        requestedQuantity = listItemRequest.requestedQuantity,
        requestedQuantityUnitOfMeasure = listItemRequest.unitOfMeasure?.name,
        addMode = "LIST"
    )
}

fun toCartItemUpdateRequest(
    cartItemResponse: CartItemResponse,
    listId: UUID,
    listItemId: UUID,
    listItemUpdateRequest: ListItemUpdateRequestTO
): CartItemUpdateRequest {

    val cartItemMetadata = cartItemResponse.metadata
    val listItemMetadata = getListItemMetaDataFromCart(cartItemMetadata)
    val userItemMetadata = getUserItemMetaDataFromCart(cartItemMetadata)

    val updatedListItemMetadata = setCartItemMetaDataForListItem(
        itemType = listItemMetadata?.itemType,
        itemState = listItemMetadata?.itemState, // item state can only be update by creating and deleting items
        tenantItemMetaData = listItemUpdateRequest.metadata ?: userItemMetadata?.userMetaData
    )

    return CartItemUpdateRequest(
        cartId = listId,
        cartItemId = listItemId,
        tenantItemName = listItemUpdateRequest.itemTitle ?: cartItemResponse.tenantItemName,
        notes = listItemUpdateRequest.itemNote ?: cartItemResponse.notes,
        metadata = updatedListItemMetadata,
        requestedQuantity = listItemUpdateRequest.requestedQuantity ?: cartItemResponse.requestedQuantity,
        requestedQuantityUnitOfMeasure = cartItemResponse.requestedQuantityUnitOfMeasure,
        tenantReferenceId = cartItemResponse.tenantReferenceId
    )
}

fun toListItemRequestTO(cartItemResponse: CartItemResponse): ListItemRequestTO {
    val cartItemMetadata = cartItemResponse.metadata

    val listItemMetadata = getListItemMetaDataFromCart(cartItemMetadata)
    val userItemMetadata = getUserItemMetaDataFromCart(cartItemMetadata)

    return ListItemRequestTO(
        itemType = listItemMetadata?.itemType ?: ItemType.TCIN,
        itemRefId = cartItemResponse.tenantReferenceId!!,
        channel = cartItemResponse.itemAddChannel,
        tcin = cartItemResponse.tcin,
        itemTitle = cartItemResponse.tenantItemName,
        itemNote = cartItemResponse.notes,
        requestedQuantity = cartItemResponse.requestedQuantity,
        unitOfMeasure = cartItemResponse.requestedQuantityUnitOfMeasure?.let { UnitOfMeasure.valueOf(it) } ?: UnitOfMeasure.EACHES,
        metadata = userItemMetadata?.userMetaData
    )
}

fun toListItemResponseTO(cartItemResponse: CartItemResponse, listItemState: LIST_ITEM_STATE? = null, offerCount: Int = 0): ListItemResponseTO {
    val cartItemMetadata = cartItemResponse.metadata

    val listItemMetadata = getListItemMetaDataFromCart(cartItemMetadata)
    val userItemMetadata = getUserItemMetaDataFromCart(cartItemMetadata)

    return ListItemResponseTO(
        listItemId = cartItemResponse.cartItemId,
        itemRefId = cartItemResponse.tenantReferenceId!!,
        channel = cartItemResponse.itemAddChannel,
        tcin = cartItemResponse.tcin,
        itemTitle = cartItemResponse.tenantItemName ?: cartItemResponse.shortDescription,
        requestedQuantity = cartItemResponse.requestedQuantity,
        unitOfMeasure = cartItemResponse.requestedQuantityUnitOfMeasure?.let { UnitOfMeasure.valueOf(it) },
        itemNote = cartItemResponse.notes,
        price = cartItemResponse.price,
        listPrice = cartItemResponse.listPrice,
        offerCount = offerCount,
        images = cartItemResponse.images,
        metadata = userItemMetadata?.userMetaData,
        itemType = listItemMetadata?.itemType,
        relationshipType = cartItemResponse.relationshipType,
        itemState = listItemState ?: listItemMetadata?.itemState,
        addedTs = cartItemResponse.createdAt.let { addZ(it) },
        lastModifiedTs = cartItemResponse.updatedAt.let { addZ(it) }
    )
}

fun addZ(str: LocalDateTime?): String? {
    if (str == null) {
        return null
    }
    return str.toString() + "Z"
}

fun getCompletedCartRequest(guestId: String, listType: String, locationId: String, abandonAfterDuration: AbandonAfterDuration, cartNumber: UUID?): CartPostRequest {
    val metadata = setCartMetaDataFromList(
        listStatus = LIST_STATUS.COMPLETED)

    return CartPostRequest(cartNumber = cartNumber.toString(),
        cartLocationId = locationId, abandonAfterDuration = abandonAfterDuration, cartType = CartType.LIST.value,
        tenantCartName = Constants.COMPLETED_CART_NAME, guestId = guestId, cartSubchannel = listType,
        metadata = metadata,
        getContentsOptions = arrayOf(GetContentsOptions(option = CartOption.ITEMS.name, enabled = true, stopOnFailure = false),
            GetContentsOptions(option = CartOption.PRICES.name, enabled = true, stopOnFailure = false)))
}

private val validator = Validation.buildDefaultValidatorFactory().validator

fun <T> validate(entity: T): T {
    val fieldErrors = validator.validate(entity).map { it.message }.toList()
    if (!fieldErrors.isNullOrEmpty()) {
        throw InternalServerException(RESPONSE_BODY_VIOLATION_ERROR_CODE(fieldErrors))
    }
    return entity
}

fun <T> validate(entities: List<T>): List<T> {
    val fieldErrors = entities.flatMap { validator.validate(it).map { it.message } }.toList()
    if (!fieldErrors.isNullOrEmpty()) {
        throw InternalServerException(RESPONSE_BODY_VIOLATION_ERROR_CODE(fieldErrors))
    }
    return entities
}
