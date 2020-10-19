package com.tgt.lists.atlas.kafka.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tgt.lists.atlas.api.transport.EditItemSortOrderRequestTO
import com.tgt.lists.msgbus.EventType

data class EditListItemSortOrderActionEvent(
    @JsonProperty("edit_list_item_sort_order_request")
    val editItemSortOrderRequestTO: EditItemSortOrderRequestTO,

    @JsonProperty("retry_state")
    var retryState: String? = null
) {

    companion object {
        private val jsonMapper = jacksonObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)

        @JvmStatic
        fun getEventType(): EventType {
            return "EDIT-LIST-ITEM-SORT-ORDER-NOTIFY-EVENT"
        }

        @JvmStatic
        fun deserialize(byteArray: ByteArray): EditListItemSortOrderActionEvent {
            return jsonMapper.readValue(byteArray, EditListItemSortOrderActionEvent::class.java)
        }
    }
}
