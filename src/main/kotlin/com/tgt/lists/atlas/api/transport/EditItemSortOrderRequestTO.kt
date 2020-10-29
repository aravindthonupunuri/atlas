package com.tgt.lists.atlas.api.transport

import com.tgt.lists.atlas.api.util.Direction
import java.util.*
import javax.validation.constraints.NotNull

data class EditItemSortOrderRequestTO(
    @field:NotNull(message = "Guest Id for updating sort order of the items must not be empty") val guestId: String,
    @field:NotNull(message = "List Id for updating sort order of the items must not be empty") val listId: UUID,
    @field:NotNull(message = "Primary Item id to move must not be empty") val primaryItemId: UUID,
    @field:NotNull(message = "Secondary Item id must not be empty") val secondaryItemId: UUID,
    @field:NotNull(message = "Direction must not be empty") val direction: Direction
)
