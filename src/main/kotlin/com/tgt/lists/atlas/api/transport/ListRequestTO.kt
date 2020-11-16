package com.tgt.lists.atlas.api.transport

import com.tgt.lists.atlas.api.util.LIST_STATE
import javax.validation.constraints.NotEmpty

data class ListRequestTO(
    @field:NotEmpty(message = "Channel must not be empty") val channel: String,
    @field:NotEmpty(message = "List title must not be empty") val listTitle: String,
    @field:NotEmpty(message = "List sub type must not be empty") val listSubType: String,
    @field:NotEmpty(message = "List state must not be empty") val listState: LIST_STATE,
    val subChannel: String? = null,
    val locationId: Long? = null,
    val shortDescription: String? = null,
    val defaultList: Boolean = false,
    val agentId: String? = null,
    val metadata: Map<String, Any>? = null
)
