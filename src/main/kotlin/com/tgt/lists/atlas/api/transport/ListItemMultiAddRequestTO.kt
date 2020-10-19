package com.tgt.lists.atlas.api.transport

import javax.validation.constraints.NotNull

data class ListItemMultiAddRequestTO(
    @field:NotNull(message = "The items to add is not present, enter comma separated TCINs") val items: List<ListItemRequestTO>
)
