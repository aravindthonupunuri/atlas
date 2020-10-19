package com.tgt.lists.atlas.api.util

fun isItemTypeParent(itemRelationshipType: String?): Boolean {
    return itemRelationshipType != null && itemRelationshipType.contains("parent", true)
}
