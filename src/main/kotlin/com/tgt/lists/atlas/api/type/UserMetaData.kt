package com.tgt.lists.atlas.api.type

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.UntypedObjectDeserializer
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

data class UserMetaData(
    @JsonProperty("metadata")
    @JsonDeserialize(using = UntypedObjectDeserializer::class)
    val metadata: Map<String, Any>
) {
    companion object {
        val mapper = jacksonObjectMapper()

        fun toEntityMetadata(userMetaData: UserMetaData?): String? {
            return userMetaData?.metadata?.let { mapper.writeValueAsString(it) }
        }

        fun toUserMetaData(entityMetaData: String?): UserMetaData? {
            return entityMetaData?.let {
                UserMetaData(mapper.readValue(it))
            }
        }
    }
}
