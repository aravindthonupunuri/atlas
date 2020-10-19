package com.tgt.lists.atlas.api.transport

import com.fasterxml.jackson.annotation.JsonIgnore
import java.util.*
import javax.validation.constraints.NotNull

data class ListItemDeleteResponseTO(
    @field:NotNull(message = "List id must not be empty") val listId: UUID? = null,
    @field:NotNull(message = "List item id must not be empty") val listItemId: UUID? = null,
    @JsonIgnore val status: Boolean = true
)
