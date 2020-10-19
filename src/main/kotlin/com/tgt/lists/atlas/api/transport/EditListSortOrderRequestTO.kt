package com.tgt.lists.atlas.api.transport

import com.tgt.lists.atlas.api.util.Direction
import java.util.*
import javax.validation.constraints.NotNull

data class EditListSortOrderRequestTO(
    @field:NotNull(message = "Primary list id to move must not be empty") val primaryListId: UUID,
    @field:NotNull(message = "Secondary list id must not be empty") val secondaryListId: UUID,
    @field:NotNull(message = "Direction must not be empty") val direction: Direction
)
