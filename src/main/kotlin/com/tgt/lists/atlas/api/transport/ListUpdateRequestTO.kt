package com.tgt.lists.atlas.api.transport

import com.tgt.lists.atlas.api.service.transform.list.UserMetaDataTransformationStep
import com.tgt.lists.atlas.api.util.AppErrorCodes.REQUEST_BODY_VIOLATION_ERROR_CODE
import com.tgt.lists.atlas.api.util.LIST_STATE
import com.tgt.lists.common.components.exception.BadRequestException

data class ListUpdateRequestTO(
    val listTitle: String? = null,
    val shortDescription: String? = null,
    val defaultList: Boolean? = null,
    val listState: LIST_STATE? = null, // app can always change the state of the list depending on its business case
    val metadata: Map<String, Any>? = null,
    val userMetaDataTransformationStep: UserMetaDataTransformationStep? = null
) {
    fun validate(): ListUpdateRequestTO {
        if (defaultList == false) throw BadRequestException(REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("Default List cannot be updated to false")))
        return this
    }
}
