package com.tgt.lists.atlas.api.transport

import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull

data class ListRequestTO(
    @field:NotNull(message = "Channel must not be empty") val channel: String,
    @field:NotEmpty(message = "List title must not be empty") val listTitle: String,
    val locationId: Long? = null,
    val shortDescription: String? = null,
    val defaultList: Boolean = false,
    val agentId: String? = null,
    val metadata: Map<String, Any>? = null
)
