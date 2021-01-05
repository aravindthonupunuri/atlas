package com.tgt.lists.atlas.kafka.model

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tgt.lists.atlas.api.type.EventType
import com.tgt.lists.atlas.api.type.ItemType
import com.tgt.lists.atlas.api.type.LIST_ITEM_STATE
import java.time.LocalDateTime
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
data class UpdateListItemNotifyEvent(
    @JsonProperty("guest_id")
    val guestId: String,

    @JsonProperty("list_id")
    val listId: UUID,

    @JsonProperty("item_id")
    val itemId: UUID,

    @JsonProperty("item_state")
    val itemState: LIST_ITEM_STATE,

    @JsonProperty("item_type")
    val itemType: ItemType,

    @JsonProperty("tcin")
    val tcin: String?,

    @JsonProperty("item_title")
    val itemTitle: String?,

    @JsonProperty("channel")
    val channel: String? = null,

    @JsonProperty("sub_channel")
    val subChannel: String? = null,

    @JsonProperty("item_requested_quantity")
    val itemRequestedQuantity: Int? = null,

    @JsonProperty("user_meta_data")
    val userItemMetaDataTO: Map<String, Any>? = null,

    @JsonProperty("item_note")
    val itemNote: String? = null,

    @JsonProperty("item_fulfilled_quantity")
    val itemFulfilledQuantity: Int? = null,

    @JsonProperty("added_date_time")
    @JsonSerialize(using = LocalDateTimeSerializer::class)
    @JsonDeserialize(using = LocalDateTimeDeserializer::class)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    val addedDate: LocalDateTime? = null,

    @JsonProperty("last_modified_date_time")
    @JsonSerialize(using = LocalDateTimeSerializer::class)
    @JsonDeserialize(using = LocalDateTimeDeserializer::class)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    val lastModifiedDate: LocalDateTime? = null,

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
