package com.tgt.lists.atlas.api.transport

import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import java.util.*
import javax.validation.constraints.NotNull

data class ListItemMultiStateUpdateRequestTO(
    @field:NotNull(message = "item state not present") val itemState: LIST_ITEM_STATE,
    @field:NotNull(message = "enter list of ListItemIds") val itemIds: List<UUID>
)
