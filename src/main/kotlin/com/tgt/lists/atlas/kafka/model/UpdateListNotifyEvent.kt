package com.tgt.lists.atlas.kafka.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tgt.lists.atlas.api.util.EventType
import com.tgt.lists.atlas.api.util.LIST_STATE
import java.util.*

data class UpdateListNotifyEvent(
    @JsonProperty("guest_id")
    val guestId: String,

    @JsonProperty("list_id")
    val listId: UUID,

    @JsonProperty("list_type")
    val listType: String,

    @JsonProperty("list_title")
    val listTitle: String?,

    @JsonProperty("list_state")
    val listState: LIST_STATE?,

    @JsonProperty("user_meta_data")
    val userMetaData: Map<String, Any>? = null,

    @JsonProperty("retry_state")
    var retryState: String? = null
) {

    companion object {
        private val jsonMapper = jacksonObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)

        @JvmStatic
        fun getEventType(): EventType {
            return "CREATE-LIST-NOTIFY-EVENT"
        }

        @JvmStatic
        fun deserialize(byteArray: ByteArray): UpdateListNotifyEvent {
            return jsonMapper.readValue(byteArray, UpdateListNotifyEvent::class.java)
        }
    }
}
