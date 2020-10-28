package com.tgt.lists.atlas.api.transport

import com.tgt.lists.atlas.api.util.AppErrorCodes
import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.atlas.api.util.UnitOfMeasure
import com.tgt.lists.atlas.api.validator.validateItemType
import com.tgt.lists.common.components.exception.BadRequestException
import javax.validation.constraints.NotNull

data class ListItemRequestTO(
    @field:NotNull(message = "Item type must not be empty") val itemType: ItemType,
    @field:NotNull(message = "Item ref id must not be empty") val itemRefId: String,
    val channel: String? = null,
    val tcin: String?,
    val itemTitle: String? = null,
    val itemNote: String? = null,
    val requestedQuantity: Int? = null,
    val unitOfMeasure: UnitOfMeasure? = UnitOfMeasure.EACHES,
    val metadata: Map<String, Any>? = null
) {

    init {
        this.validate()
    }

    fun validate(): ListItemRequestTO {
        if (requestedQuantity != null && requestedQuantity < 1) throw BadRequestException(AppErrorCodes.REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("Invalid Requested quantity, must be 1 or greater")))
        validateItemType(itemType, tcin, itemTitle)
        return this
    }
}