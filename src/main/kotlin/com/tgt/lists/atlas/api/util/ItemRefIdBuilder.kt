package com.tgt.lists.atlas.api.util

import com.tgt.lists.atlas.api.type.ItemType
import java.lang.RuntimeException

/**
 * Unique Item Reference Id builder
 */
class ItemRefIdBuilder {
    companion object {
        fun buildItemRefId(itemType: ItemType, businessId: String): String {
            if (businessId.isNotEmpty()) {
                return "${itemType.value}$businessId"
            } else {
                throw RuntimeException("Empty businessId passed for creating item refId")
            }
        }
    }
}