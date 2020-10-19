package com.tgt.lists.atlas.api.transport

import com.tgt.lists.common.components.exception.BadRequestException
import com.tgt.lists.atlas.api.util.AppErrorCodes
import com.tgt.lists.atlas.api.util.AppErrorCodes.ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE
import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.atlas.api.util.UnitOfMeasure
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
        when (itemType) {
            ItemType.GENERIC_ITEM -> validateGenericItem()
            ItemType.TCIN -> validateTcinItem()
            ItemType.OFFER -> validateOfferItem()
        }
        return this
    }

    private fun validateTcinItem() {
        if (this.tcin == null || this.tcin.trim().toIntOrNull() == null) throw BadRequestException(ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("Required field tcin is missing or invalid")))
        if (this.itemTitle != null) throw BadRequestException(ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("user should not set item_title for tcin item")))
    }

    private fun validateGenericItem() {
        if (this.tcin != null) throw BadRequestException(ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("Unexpected field tcin present for generic item")))
        val itemTitle: String = this.itemTitle ?: throw BadRequestException(ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("Required field item title is missing")))
        if (itemTitle.trim().toIntOrNull() != null) throw BadRequestException(ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("Invalid item title")))
    }

    private fun validateOfferItem() {
        if (this.tcin != null) throw BadRequestException(ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("Unexpected field tcin present for offer item")))
        if (this.itemTitle != null) throw BadRequestException(ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("Unexpected field item title present for offer item")))
    }
}