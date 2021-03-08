package com.tgt.lists.atlas.kafka.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tgt.lists.atlas.api.type.EventType
import com.tgt.lists.atlas.api.type.LIST_ITEM_STATE
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
data class DeleteListItemNotifyEvent(
    @JsonProperty("list_id")
    val listId: UUID,

    @JsonProperty("items")
    val deleteListItems: List<MultiDeleteListItem>,

    @JsonProperty("performed_by")
    val performedBy: String? = null,

    @JsonProperty("retry_state")
    var retryState: String? = null
) {
    companion object {
        // jacksonObjectMapper() returns a normal ObjectMapper with the KotlinModule registered
        private val jsonMapper = jacksonObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)

        @JvmStatic
        fun getEventType(): EventType {
            return "DELETE-LIST-ITEM-NOTIFY-EVENT"
        }

        @JvmStatic
        fun deserialize(byteArray: ByteArray): DeleteListItemNotifyEvent {
            return jsonMapper.readValue(byteArray, DeleteListItemNotifyEvent::class.java)
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class MultiDeleteListItem(
    @JsonProperty("item_id")
val itemId: UUID,

    @JsonProperty("tcin")
val tcin: String?,

    @JsonProperty("item_title")
val itemTitle: String?,

    @JsonProperty("item_requested_quantity")
val itemRequestedQuantity: Int?,

    @JsonProperty("item_state")
val itemState: LIST_ITEM_STATE?,

    @JsonProperty("user_meta_data")
val userItemMetaDataTO: Map<String, Any>? = null
)
