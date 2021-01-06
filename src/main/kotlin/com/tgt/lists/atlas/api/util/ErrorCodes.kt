package com.tgt.lists.atlas.api.util

object ErrorCodes {
    val LIST_NOT_FOUND_ERROR_CODE = Pair(1001, "List not found")
    val LIST_ITEM_NOT_FOUND_ERROR_CODE = Pair(1002, "List Item not found")
    val LIST_ITEM_ENTITY_VIOLATION_ERROR_CODE = Pair(1003, "Invalid List Item Entity")
    val MAX_LISTS_COUNT_VIOLATION_ERROR_CODE = Pair(1004, "Exceeding max allowed guest lists")
    val MAX_LIST_ITEMS_COUNT_VIOLATION_ERROR_CODE = Pair(1005, "Exceeding max allowed list items")
    val UPDATE_DEFAULT_LIST_VIOLATION_ERROR_CODE = Pair(1006, "Fixed default list enabled, Not allowed to update default list")
    val DELETE_LIST_ITEMS_VIOLATION_ERROR_CODE = Pair(1007, "Both itemIds and itemIncludeFields given, select either one of them")
    val DELETE_LIST_ITEMS_INCLUDED_FIELD_VIOLATION_ERROR_CODE = Pair(1008, "Invalid delete list items included field")
    val LIST_SORT_ORDER_ERROR_CODE = Pair(1009, "List sort order exception")
    val LIST_ITEM_SORT_ORDER_ERROR_CODE = Pair(1010, "List item sort order exception")
}
