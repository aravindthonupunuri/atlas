package com.tgt.lists.atlas.kafka.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tgt.lists.atlas.api.transport.ListItemMetaDataTO
import com.tgt.lists.atlas.api.util.EventType
import java.util.*

data class UpdateListItemNotifyEvent(
    @JsonProperty("guest_id")
    val guestId: String,

    @JsonProperty("list_id")
    val listId: UUID,

    @JsonProperty("item_id")
    val itemId: UUID,

    @JsonProperty("tcin")
    val tcin: String?,

    @JsonProperty("item_title")
    val itemTitle: String?,

    @JsonProperty("item_requested_quantity")
    val itemRequestedQuantity: Int?,

    @JsonProperty("list_item_metadata")
    val listItemMetaDataTO: ListItemMetaDataTO?, // TODO Eventually remove this attribute

    @JsonProperty("user_meta_data")
    val userItemMetaDataTO: Map<String, Any>? = null,

    @JsonProperty("retry_state")
    var retryState: String? = null
) {
    companion object {
        // jacksonObjectMapper() returns a normal ObjectMapper with the KotlinModule registered
        private val jsonMapper = jacksonObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)

        @JvmStatic
        fun getEventType(): EventType {
            return "UPDATE-LIST-ITEM-NOTIFY-EVENT"
        }

        @JvmStatic
        fun deserialize(byteArray: ByteArray): UpdateListItemNotifyEvent {
            return jsonMapper.readValue(byteArray, UpdateListItemNotifyEvent::class.java)
        }
    }
}
