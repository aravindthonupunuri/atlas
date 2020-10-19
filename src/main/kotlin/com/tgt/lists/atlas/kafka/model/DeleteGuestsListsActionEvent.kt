package com.tgt.lists.atlas.kafka.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tgt.lists.atlas.api.util.EventType

data class DeleteGuestsListsActionEvent(
    @JsonProperty("message_id")
    val messageId: String,

    @JsonProperty("guest_ids")
    val guestIds: List<String>,

    @JsonProperty("retry_state")
    var retryState: String? = null
) {
    companion object {
        private val jsonMapper = jacksonObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)

        @JvmStatic
        fun getEventType(): EventType {
            return "DELETE-GUESTS-LISTS-ACTION-EVENT"
        }

        @JvmStatic
        fun deserialize(byteArray: ByteArray): DeleteGuestsListsActionEvent {
            return jsonMapper.readValue(byteArray, DeleteGuestsListsActionEvent::class.java)
        }
    }
}
