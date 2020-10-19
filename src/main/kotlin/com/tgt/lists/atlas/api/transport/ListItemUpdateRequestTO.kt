package com.tgt.lists.atlas.api.transport

import com.tgt.lists.common.components.exception.BadRequestException
import com.tgt.lists.atlas.api.service.transform.list_items.UserItemMetaDataTransformationStep
import com.tgt.lists.atlas.api.util.AppErrorCodes
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import io.micronaut.core.annotation.Introspected

@Introspected
data class ListItemUpdateRequestTO(
    val itemTitle: String? = null,
    val itemNote: String? = null,
    val metadata: Map<String, Any>? = null,
    val itemState: LIST_ITEM_STATE? = null,
    val requestedQuantity: Int? = null,
    val userItemMetaDataTransformationStep: UserItemMetaDataTransformationStep? = null
) {
    fun validate(): ListItemUpdateRequestTO {
        if ((isNullOrEmpty(itemTitle) &&
                isNullOrEmpty(itemNote) &&
                this.metadata == null &&
                this.itemState == null &&
                this.requestedQuantity == null) ||
            (itemTitle != null && itemTitle.trim().isBlank())) {
            throw BadRequestException(AppErrorCodes.ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("Empty request body or empty itemTitle")))
        }
        return this
    }

    fun onlyItemStateUpdate(): Boolean {
        var result = true
        for (f in this.javaClass.declaredFields) {
            if (!f.isSynthetic && ((f.name != "itemState" && f.get(this) != null) ||
                    (f.name == "itemState" && f.get(this) == null))) {
                result = false
            }
        }
        return result
    }

    private fun isNullOrEmpty(value: String?): Boolean {
        if (value == null || value.trim().isBlank()) return true

        return false
    }
}
