package com.tgt.lists.atlas.api.transport

import com.fasterxml.jackson.annotation.JsonProperty

data class UserMetaDataTO(
    @JsonProperty("user_meta_data")
    val userMetaData: Map<String, Any>? = null
)
