package com.tgt.lists.atlas.api.transport

import java.util.*
import javax.validation.constraints.NotNull

data class ListItemsDeleteResponseTO(
    @field:NotNull(message = "List id must not be empty") val listId: UUID? = null,
    val itemIds: List<UUID>? = null
)
