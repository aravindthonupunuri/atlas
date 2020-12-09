package com.tgt.lists.atlas.api.transport

import com.tgt.lists.atlas.api.type.UserMetaData
import com.tgt.lists.atlas.api.type.ItemType
import com.tgt.lists.atlas.api.type.UnitOfMeasure
import com.tgt.lists.atlas.api.validator.validateItemType
import com.tgt.lists.common.components.exception.BadRequestException
import com.tgt.lists.common.components.exception.BaseErrorCodes.REQUEST_BODY_VIOLATION_ERROR_CODE
import com.tgt.lists.common.components.exception.ErrorCode
import javax.validation.constraints.NotNull

data class ListItemRequestTO(
    @field:NotNull(message = "Item type must not be empty") val itemType: ItemType,
    @field:NotNull(message = "Item ref id must not be empty") val itemRefId: String,
    val channel: String? = null,
    val subChannel: String? = null,
    val tcin: String?,
    val itemTitle: String? = null,
    val itemNote: String? = null,
    val requestedQuantity: Int? = null,
    val fulfilledQuantity: Int? = null,
    val unitOfMeasure: UnitOfMeasure? = UnitOfMeasure.EACHES,
    val agentId: String? = null,
    val metadata: UserMetaData? = null
) {

    init {
        this.validate()
    }

    fun validate(): ListItemRequestTO {
        if (requestedQuantity != null && requestedQuantity < 1) throw BadRequestException(ErrorCode(REQUEST_BODY_VIOLATION_ERROR_CODE.first, REQUEST_BODY_VIOLATION_ERROR_CODE.second, arrayListOf("Invalid Requested quantity, must be 1 or greater")))
        validateItemType(itemType, tcin, itemTitle)
        return this
    }
}