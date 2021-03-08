package com.tgt.lists.atlas.kafka.model

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tgt.lists.atlas.api.type.EventType
import com.tgt.lists.atlas.api.type.LIST_STATE
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
data class UpdateListNotifyEvent(
    @JsonProperty("guest_id")
    val guestId: String,

    @JsonProperty("list_id")
    val listId: UUID,

    @JsonProperty("list_type")
    val listType: String,

    @JsonProperty("list_sub_type")
    val listSubType: String? = null,

    @JsonProperty("list_title")
    val listTitle: String?,

    @JsonProperty("channel")
    val channel: String? = null,

    @JsonProperty("sub_channel")
    val subChannel: String? = null,

    @JsonProperty("list_state")
    val listState: LIST_STATE?,

    @JsonProperty("user_meta_data")
    val userMetaData: Map<String, Any>? = null,

    @JsonProperty("expiration")
    @JsonSerialize(using = LocalDateSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val expiration: LocalDate,

    @JsonProperty("short_description")
    val shortDescription: String? = null,

    @JsonProperty("default_list")
    val defaultList: Boolean? = null,

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

    @JsonProperty("performed_by")
    val performedBy: String? = null,

    @JsonProperty("retry_state")
    var retryState: String? = null
) {

    companion object {
        private val jsonMapper = jacksonObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)

        @JvmStatic
        fun getEventType(): EventType {
            return "UPDATE-LIST-NOTIFY-EVENT"
        }

        @JvmStatic
        fun deserialize(byteArray: ByteArray): UpdateListNotifyEvent {
            return jsonMapper.readValue(byteArray, UpdateListNotifyEvent::class.java)
        }
    }
}
