package com.tgt.lists.atlas.api.transport

import com.fasterxml.jackson.annotation.JsonInclude
import com.tgt.lists.atlas.api.type.UserMetaData
import com.tgt.lists.atlas.api.type.LIST_STATE
import java.time.LocalDate
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
    val metadata: UserMetaData?,
    val pendingListItems: List<ListItemResponseTO>? = null,
    val completedListItems: List<ListItemResponseTO>? = null,
    val addedTs: String?,
    val lastModifiedTs: String?,
    val maxPendingItemsCount: Int? = 0,
    val maxCompletedItemsCount: Int? = 0,
    val maxPendingPageCount: Int? = null,
    val maxCompletedPageCount: Int? = null,
    val expiration: LocalDate? = null
)
