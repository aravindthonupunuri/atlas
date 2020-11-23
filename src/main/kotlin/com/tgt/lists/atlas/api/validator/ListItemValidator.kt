package com.tgt.lists.atlas.api.validator

import com.tgt.lists.atlas.api.util.AppErrorCodes
import com.tgt.lists.atlas.api.type.ItemType
import com.tgt.lists.common.components.exception.BadRequestException

fun validateItemType(itemType: ItemType, tcin: String?, itemTitle: String?) {
    when (itemType) {
        ItemType.TCIN -> {
            if (tcin == null || tcin.trim().toIntOrNull() == null) throw BadRequestException(AppErrorCodes.ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("Required field tcin is missing or invalid")))
            if (itemTitle != null) throw BadRequestException(AppErrorCodes.ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("user should not set item_title for tcin item")))
        }
        ItemType.GENERIC_ITEM -> {
            if (tcin != null) throw BadRequestException(AppErrorCodes.ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("Unexpected field tcin present for generic item")))
            val title: String = itemTitle ?: throw BadRequestException(AppErrorCodes.ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("Required field item title is missing")))
            if (title.trim().toIntOrNull() != null) throw BadRequestException(AppErrorCodes.ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("Invalid item title")))
        }
        ItemType.OFFER -> {
            if (tcin != null) throw BadRequestException(AppErrorCodes.ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("Unexpected field tcin present for offer item")))
            if (itemTitle != null) throw BadRequestException(AppErrorCodes.ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("Unexpected field item title present for offer item")))
        }
    }
}