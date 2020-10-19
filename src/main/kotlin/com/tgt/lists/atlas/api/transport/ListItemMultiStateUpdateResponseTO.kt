package com.tgt.lists.atlas.api.transport

import java.util.*
import javax.validation.constraints.NotNull

data class ListItemMultiStateUpdateResponseTO(
    @field:NotNull(message = "List id must not be empty") val listId: UUID? = null,
    val successListItemIds: List<UUID>? = null,
    val failedListItemIds: List<UUID>? = null

)