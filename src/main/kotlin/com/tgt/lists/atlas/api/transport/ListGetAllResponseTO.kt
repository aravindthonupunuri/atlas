package com.tgt.lists.atlas.api.transport

import com.tgt.lists.atlas.api.type.UserMetaData
import com.tgt.lists.atlas.api.type.LIST_STATE
import java.util.*

data class ListGetAllResponseTO(
    val listId: UUID?,
    val channel: String? = null,
    val subChannel: String? = null,
    val listType: String?,
    val listSubType: String? = null,
    val listState: LIST_STATE? = null,
    val listTitle: String?,
    val defaultList: Boolean = false,
    val shortDescription: String?,
    val agentId: String? = null,
    val metadata: UserMetaData?,
    val addedTs: String? = null,
    val lastModifiedTs: String? = null,
    val maxListsCount: Int = -1,
    val pendingItemsCount: Int = -1,
    val completedItemsCount: Int = -1,
    val totalItemsCount: Int = -1,
    val pendingItems: List<ListItemResponseTO>? = null,
    val completedItems: List<ListItemResponseTO>? = null
)