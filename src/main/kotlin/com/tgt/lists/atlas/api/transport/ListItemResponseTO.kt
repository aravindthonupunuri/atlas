package com.tgt.lists.atlas.api.transport

import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.api.util.UnitOfMeasure
import com.tgt.lists.cart.transport.Image
import java.util.*
import javax.validation.constraints.NotNull

data class ListItemResponseTO(
    @field:NotNull(message = "List item id must not be empty") val listItemId: UUID? = null,
    @field:NotNull(message = "Item type must not be empty") val itemType: ItemType? = null,
    @field:NotNull(message = "Item ref id must not be empty") val itemRefId: String,
    val channel: String? = null,
    val tcin: String? = null,
    val itemTitle: String? = null,
    val itemNote: String? = null,
    val requestedQuantity: Int? = 1,
    val unitOfMeasure: UnitOfMeasure? = null,
    val metadata: Map<String, Any>? = null,
    val price: Float? = null,
    val listPrice: Float? = null,
    val offerCount: Int = 0,
    val images: Image? = null,
    val relationshipType: String? = null,
    val itemState: LIST_ITEM_STATE? = LIST_ITEM_STATE.PENDING,
    val addedTs: String? = null,
    val lastModifiedTs: String? = null
)
