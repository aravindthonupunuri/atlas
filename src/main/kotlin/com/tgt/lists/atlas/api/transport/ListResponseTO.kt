package com.tgt.lists.atlas.api.transport

import com.fasterxml.jackson.annotation.JsonInclude
import com.tgt.lists.atlas.api.util.LIST_STATE
import java.util.*
import javax.validation.constraints.*

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ListResponseTO(
    @field:NotEmpty(message = "List id must not be empty") val listId: UUID?,
    @field:NotEmpty(message = "Channel must not be empty") val channel: String?,
    @field:NotEmpty(message = "List type must not be empty") val listType: String?,
    @field:NotEmpty(message = "List type must not be empty") val listSubType: String?,
    @field:NotEmpty(message = "List type must not be empty") val listState: LIST_STATE?,
    @field:NotEmpty(message = "List title must not be empty") val listTitle: String?,
    val subChannel: String? = null,
    val shortDescription: String?,
    val agentId: String?,
    val defaultList: Boolean? = false,
    val metadata: Map<String, Any>?,
    val pendingListItems: List<ListItemResponseTO>? = null,
    val completedListItems: List<ListItemResponseTO>? = null,
    val addedTs: String?,
    val lastModifiedTs: String?,
    val maxPendingItemsCount: Int? = 0,
    val maxCompletedItemsCount: Int? = 0,
    val maxPendingPageCount: Int? = null,
    val maxCompletedPageCount: Int? = null
)
