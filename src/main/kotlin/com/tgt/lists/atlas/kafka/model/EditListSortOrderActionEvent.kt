package com.tgt.lists.atlas.kafka.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tgt.lists.atlas.api.transport.EditListSortOrderRequestTO
import com.tgt.lists.msgbus.EventType

data class EditListSortOrderActionEvent(
    @JsonProperty("guest_id")
    val guestId: String,

    @JsonProperty("edit_list_sort_order_request")
    val editListSortOrderRequestTO: EditListSortOrderRequestTO,

    @JsonProperty("retry_state")
    var retryState: String? = null
) {

    companion object {
        private val jsonMapper = jacksonObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)

        @JvmStatic
        fun getEventType(): EventType {
            return "EDIT-LIST-SORT-ORDER-NOTIFY-EVENT"
        }

        @JvmStatic
        fun deserialize(byteArray: ByteArray): EditListSortOrderActionEvent {
            return jsonMapper.readValue(byteArray, EditListSortOrderActionEvent::class.java)
        }
    }
}
