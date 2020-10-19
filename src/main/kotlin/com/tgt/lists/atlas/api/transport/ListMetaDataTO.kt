package com.tgt.lists.atlas.api.transport

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.tgt.lists.atlas.api.util.LIST_STATUS

@JsonIgnoreProperties(ignoreUnknown = true)
data class ListMetaDataTO(
    @JsonAlias("default_list_ind", "default_list_flag")
    @JsonProperty("default_list")
    val defaultList: Boolean = false,

    @JsonProperty("list_status")
    val listStatus: LIST_STATUS? = LIST_STATUS.PENDING
)
