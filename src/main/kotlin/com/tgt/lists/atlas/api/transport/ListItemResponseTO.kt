package com.tgt.lists.atlas.api.transport

import com.tgt.lists.atlas.api.type.UserMetaData
import com.tgt.lists.atlas.api.type.ItemType
import com.tgt.lists.atlas.api.type.LIST_ITEM_STATE
import com.tgt.lists.atlas.api.type.UnitOfMeasure
import java.util.*
import javax.validation.constraints.NotNull

data class ListItemResponseTO(
    @field:NotNull(message = "List item id must not be empty") val listItemId: UUID? = null,
    @field:NotNull(message = "Item type must not be empty") val itemType: ItemType? = null,
    @field:NotNull(message = "Item ref id must not be empty") val itemRefId: String,
    val channel: String? = null,
    val subChannel: String? = null,
    val tcin: String? = null,
    val itemTitle: String? = null,
    val itemNote: String? = null,
    val requestedQuantity: Int? = 1,
    val fulfilledQuantity: Int? = null,
    val unitOfMeasure: UnitOfMeasure? = null,
    val metadata: UserMetaData? = null,
    val price: Float? = null,
    val listPrice: Float? = null,
    val offerCount: Int = 0,
    val relationshipType: String? = null,
    val itemState: LIST_ITEM_STATE? = LIST_ITEM_STATE.PENDING,
    val agentId: String? = null,
    val addedTs: String? = null,
    val lastModifiedTs: String? = null
)
