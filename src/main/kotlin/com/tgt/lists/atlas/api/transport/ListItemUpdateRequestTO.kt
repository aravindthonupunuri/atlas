package com.tgt.lists.atlas.api.transport

import com.tgt.lists.atlas.api.service.transform.list_items.UserItemMetaDataTransformationStep
import com.tgt.lists.atlas.api.util.AppErrorCodes
import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.api.util.isNullOrEmpty
import com.tgt.lists.atlas.api.validator.RefIdValidator
import com.tgt.lists.atlas.api.validator.validateItemType
import com.tgt.lists.common.components.exception.BadRequestException
import com.tgt.lists.common.components.exception.InternalServerException
import io.micronaut.core.annotation.Introspected

// TODO is agentid to be updated at all?
@Introspected
data class ListItemUpdateRequestTO(
    val tcin: String? = null,
    val itemTitle: String? = null,
    val itemNote: String? = null,
    val itemType: ItemType? = null,
    val itemRefId: String? = null,
    val metadata: Map<String, Any>? = null,
    val itemState: LIST_ITEM_STATE? = null,
    val requestedQuantity: Int? = null,
    val fulfilledQuantity: Int? = null,
    val refIdValidator: RefIdValidator,
    val userItemMetaDataTransformationStep: UserItemMetaDataTransformationStep? = null
) {
    init {
        this.validate()
    }

    fun validate(): ListItemUpdateRequestTO {
        if (isNullOrEmpty(tcin) &&
                isNullOrEmpty(itemTitle) &&
                isNullOrEmpty(itemNote) &&
                isNullOrEmpty(itemRefId) &&
                this.itemType == null &&
                this.metadata == null &&
                this.itemState == null &&
                this.requestedQuantity == null &&
                this.fulfilledQuantity == null &&
                this.userItemMetaDataTransformationStep == null) {
            throw BadRequestException(AppErrorCodes.ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("Empty request body")))
        }
        if ((itemTitle != null && itemTitle.trim().isBlank())) {
            throw BadRequestException(AppErrorCodes.ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("Empty itemTitle")))
        }

        // Item Type can only be updated from a GENERIC to TCIN type, so the update request can only have ItemType.TCIN as part
        // of the request, implying change in type to TCIN. If the existing item that is being updated is not of type GENERIC
        // we throw an exception.
        if (itemType != null && itemType != ItemType.TCIN) {
            throw BadRequestException(AppErrorCodes.ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("Invalid Item type, Item can only be updated from GENERIC to TCIN type ")))
        }

        // If updating item type from GENERIC to TCIN, we are validating if the required attributes like tcin is passed,
        // since GENERIC item will not be having tcin value. So when updating from GENERIC to TCIN the existing
        // GENERIC item will be having an item title which will be removed by the List item entity validation method in
        // ListItemEntity since TCIN item type should not have an item title.
        if (itemType != null && itemType == ItemType.TCIN) {
            validateItemType(itemType, tcin, itemTitle)
        }

        return this
    }

    fun validateRefId(itemType: ItemType) {
        // refIdValidator is used to figure out if the update request also requires itemRefId as part of the request.
        // itemRefId is calculated in the app layer and that could comprise of some of the attributes in the ListItemUpdateRequestTO,
        // so updating just those attributes will also cuase change in the itemRefId.
        // Like for example if we are trying to update tcin value for a TCIN item, then the request ListItemUpdateRequestTO
        // should also have itemRefId as part of ListItemUpdateRequestTO since tcin value is used to calculate the itemRefId
        // So refIdValidator interface is a required field in ListItemUpdateRequestTO, based on which we determine if
        // itemRefId is supposed to be part of ListItemUpdateRequestTO or not.
        if (refIdValidator.requireRefId(itemType, this) && isNullOrEmpty(itemRefId)) {
            throw InternalServerException(AppErrorCodes.ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("RefId is required")))
        }
    }
}
