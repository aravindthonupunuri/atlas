package com.tgt.lists.atlas.api.transport

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE

@JsonIgnoreProperties(ignoreUnknown = true)
data class ListItemMetaDataTO(
    @JsonProperty("item_type")
    val itemType: ItemType? = null,

    @JsonProperty("item_state")
    val itemState: LIST_ITEM_STATE? = LIST_ITEM_STATE.PENDING
)
