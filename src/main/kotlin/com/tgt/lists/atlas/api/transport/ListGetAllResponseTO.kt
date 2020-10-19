package com.tgt.lists.atlas.api.transport

import java.util.*

data class ListGetAllResponseTO(
    val listId: UUID?,
    val completedListId: UUID? = null,
    val channel: String? = null,
    val listType: String?,
    val listTitle: String?,
    val defaultList: Boolean = false,
    val shortDescription: String?,
    val agentId: String? = null,
    val metadata: Map<String, Any>?,
    val addedTs: String? = null,
    val lastModifiedTs: String? = null,
    val maxListsCount: Int = -1,
    val pendingItemsCount: Int = -1,
    val completedItemsCount: Int = -1,
    val totalItemsCount: Int = -1,
    val pendingItems: List<ListItemResponseTO>? = null,
    val compeletedItems: List<ListItemResponseTO>? = null
)
