package com.tgt.lists.atlas.api.transport

import com.tgt.lists.atlas.api.service.transform.list.UserMetaDataTransformationStep
import com.tgt.lists.atlas.api.type.LIST_STATE
import com.tgt.lists.atlas.api.type.UserMetaData
import com.tgt.lists.common.components.exception.BadRequestException
import com.tgt.lists.common.components.exception.BaseErrorCodes.REQUEST_BODY_VIOLATION_ERROR_CODE
import com.tgt.lists.common.components.exception.ErrorCode
import java.time.LocalDate

data class ListUpdateRequestTO(
    val listTitle: String? = null,
    val shortDescription: String? = null,
    val defaultList: Boolean? = null,
    val listState: LIST_STATE? = null, // app can always change the state of the list depending on its business case
    val metadata: UserMetaData? = null,
    val expiration: LocalDate? = null,
    // if app chooses to NOT update metadata, provide default transformation which returns back existing metadata
    val userMetaDataTransformationStep: UserMetaDataTransformationStep
) {
    fun validate(): ListUpdateRequestTO {
        if (defaultList == false) throw BadRequestException(ErrorCode(REQUEST_BODY_VIOLATION_ERROR_CODE.first, REQUEST_BODY_VIOLATION_ERROR_CODE.second, arrayListOf("Default List cannot be updated to false")))
        return this
    }
}
