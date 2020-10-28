package com.tgt.lists.atlas.api.util

fun isItemTypeParent(itemRelationshipType: String?): Boolean {
    return itemRelationshipType != null && itemRelationshipType.contains("parent", true)
}

fun isNullOrEmpty(value: String?): Boolean {
    if (value == null || value.trim().isBlank()) return true
    return false
}
