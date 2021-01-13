package com.tgt.lists.atlas.api.transport

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer
import com.tgt.lists.atlas.api.type.UserMetaData
import com.tgt.lists.atlas.api.type.LIST_STATE
import java.time.LocalDate
import javax.validation.constraints.NotEmpty

data class ListRequestTO(
    @field:NotEmpty(message = "Channel must not be empty") val channel: String,
    @field:NotEmpty(message = "List title must not be empty") val listTitle: String,
    @field:NotEmpty(message = "List sub type must not be empty") val listSubType: String,
    @field:NotEmpty(message = "List state must not be empty") val listState: LIST_STATE,
    @JsonDeserialize(using = LocalDateDeserializer::class)
    @JsonSerialize(using = LocalDateSerializer::class)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @field:NotEmpty(message = "List expiration must not be empty") val expiration: LocalDate,
    val subChannel: String? = null,
    val locationId: Long? = null,
    val shortDescription: String? = null,
    val defaultList: Boolean = false,
    val agentId: String? = null,
    val metadata: UserMetaData? = null
)
