package com.tgt.lists.atlas.api.util

import com.tgt.lists.common.components.exception.BaseErrorCodes
import com.tgt.lists.common.components.exception.ErrorCode

object AppErrorCodes {
    val NOT_AUTHORIZED_ERROR_CODE = BaseErrorCodes.FORBIDDEN_ERROR_CODE
    val BAD_REQUEST_ERROR_CODE = BaseErrorCodes.BAD_REQUEST_ERROR_CODE
    val RESOURCE_NOT_FOUND_ERROR_CODE = BaseErrorCodes.RESOURCE_NOT_FOUND_ERROR_CODE
    val REQUEST_BODY_VIOLATION_ERROR_CODE = BaseErrorCodes.REQUEST_BODY_VIOLATION_ERROR_CODE
    val RESPONSE_BODY_VIOLATION_ERROR_CODE = { fieldErrors: List<String>? -> ErrorCode(5, "Response body violation", fieldErrors) }
    val ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE = BaseErrorCodes.REQUEST_BODY_VIOLATION_ERROR_CODE
    val DELETE_LIST_ITEMS_INCLUDED_FIELD_VIOLATION_ERROR_CODE = ErrorCode(8, "Invalid delete list items included field")
    val LIST_MAX_PENDING_ITEM_COUNT_VIOLATION_ERROR_CODE = { fieldErrors: List<String>? -> ErrorCode(9, "Exceeding max allowed pending item count", fieldErrors) }
    val LIST_SORT_ORDER_ERROR_CODE = { fieldErrors: List<String>? -> ErrorCode(10, "Error while dealing with sort order", fieldErrors) }
    val LIST_ITEM_SORT_ORDER_ERROR_CODE = { fieldErrors: List<String>? -> ErrorCode(11, "Error while dealing with sort order", fieldErrors) }
    val INPUT_SANITIZATION_ERROR_CODE = ErrorCode(12, "Input Validation Error")
    val LIST_MAX_COUNT_VIOLATION_ERROR_CODE = { fieldErrors: List<String>? -> ErrorCode(9, "Exceeding max allowed pending item count", fieldErrors) }
    val DELETE_LIST_ITEMS_VIOLATION_ERROR_CODE = ErrorCode(10, "Both itemIds and itemIncludeFields given, select either one of them")
    val LIST_ITEM_ENTITY_VIOLATION_ERROR_CODE = { fieldErrors: List<String>? -> ErrorCode(13, "Invalid List Item Entity", fieldErrors) }
    val NOT_AUTHORIZED_LIST_ERROR_CODE = ErrorCode(14, "Authorized list Id from Url does not match primary or secondary listId")
}
