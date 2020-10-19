package com.tgt.lists.atlas.kafka.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tgt.lists.atlas.api.util.EventType
import com.tgt.lists.atlas.api.util.GuestId
import java.util.*

data class CompletionItemActionEvent(
    @JsonProperty("guest_id")
    val guestId: GuestId,

    @JsonProperty("location_id")
    val locationId: Long,

    @JsonProperty("list_id")
    val listId: UUID,

    @JsonProperty("item_id")
    val itemIds: List<UUID>,

    @JsonProperty("retry_state")
    var retryState: String? = null
) {
    companion object {
        // jacksonObjectMapper() returns a normal ObjectMapper with the KotlinModule registered
        private val jsonMapper: ObjectMapper = jacksonObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)

        @JvmStatic
        fun getEventType(): EventType {
            return "COMPLETION-ITEM-ACTION-EVENT"
        }

        @JvmStatic
        fun deserialize(byteArray: ByteArray): CompletionItemActionEvent {
            return jsonMapper.readValue<CompletionItemActionEvent>(byteArray, CompletionItemActionEvent::class.java)
        }
    }
}
