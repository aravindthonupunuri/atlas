package com.tgt.lists.atlas.api.validator

import com.tgt.lists.atlas.api.type.ItemType
import com.tgt.lists.common.components.exception.BadRequestException
import com.tgt.lists.common.components.exception.BaseErrorCodes.REQUEST_BODY_VIOLATION_ERROR_CODE
import com.tgt.lists.common.components.exception.ErrorCode

fun validateItemType(itemType: ItemType, tcin: String?, itemTitle: String?) {
    when (itemType) {
        ItemType.TCIN -> {
            if (tcin == null || tcin.trim().toIntOrNull() == null) throw BadRequestException(ErrorCode(REQUEST_BODY_VIOLATION_ERROR_CODE.first, REQUEST_BODY_VIOLATION_ERROR_CODE.second, arrayListOf("Required field tcin is missing or invalid")))
            if (itemTitle != null) throw BadRequestException(ErrorCode(REQUEST_BODY_VIOLATION_ERROR_CODE.first, REQUEST_BODY_VIOLATION_ERROR_CODE.second, arrayListOf("user should not set item_title for tcin item")))
        }
        ItemType.GENERIC_ITEM -> {
            if (tcin != null) throw BadRequestException(ErrorCode(REQUEST_BODY_VIOLATION_ERROR_CODE.first, REQUEST_BODY_VIOLATION_ERROR_CODE.second, arrayListOf("Unexpected field tcin present for generic item")))
            val title: String = itemTitle ?: throw BadRequestException(ErrorCode(REQUEST_BODY_VIOLATION_ERROR_CODE.first, REQUEST_BODY_VIOLATION_ERROR_CODE.second, arrayListOf("Required field item title is missing")))
            if (title.trim().toIntOrNull() != null) throw BadRequestException(ErrorCode(REQUEST_BODY_VIOLATION_ERROR_CODE.first, REQUEST_BODY_VIOLATION_ERROR_CODE.second, arrayListOf("Invalid item title")))
        }
        ItemType.OFFER -> {
            if (tcin != null) throw BadRequestException(ErrorCode(REQUEST_BODY_VIOLATION_ERROR_CODE.first, REQUEST_BODY_VIOLATION_ERROR_CODE.second, arrayListOf("Unexpected field tcin present for offer item")))
            if (itemTitle != null) throw BadRequestException(ErrorCode(REQUEST_BODY_VIOLATION_ERROR_CODE.first, REQUEST_BODY_VIOLATION_ERROR_CODE.second, arrayListOf("Unexpected field item title present for offer item")))
        }
    }
}