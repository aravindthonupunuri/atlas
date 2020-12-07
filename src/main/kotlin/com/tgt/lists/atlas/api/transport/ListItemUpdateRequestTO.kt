package com.tgt.lists.atlas.api.transport

import com.tgt.lists.atlas.api.service.transform.list_items.UserItemMetaDataTransformationStep
import com.tgt.lists.atlas.api.type.ItemType
import com.tgt.lists.atlas.api.type.LIST_ITEM_STATE
import com.tgt.lists.atlas.api.type.UserMetaData
import com.tgt.lists.atlas.api.util.ErrorCodes
import com.tgt.lists.atlas.api.util.isNullOrEmpty
import com.tgt.lists.atlas.api.validator.RefIdValidator
import com.tgt.lists.atlas.api.validator.validateItemType
import com.tgt.lists.common.components.exception.BadRequestException
import com.tgt.lists.common.components.exception.ErrorCode
import io.micronaut.core.annotation.Introspected

// TODO is agentid to be updated at all?
@Introspected
data class ListItemUpdateRequestTO(
    val tcin: String? = null,
    val itemTitle: String? = null,
    val itemNote: String? = null,
    val itemType: ItemType? = null,
    val metadata: UserMetaData? = null,
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
                this.itemType == null &&
                this.metadata == null &&
                this.itemState == null &&
                this.requestedQuantity == null &&
                this.fulfilledQuantity == null &&
                this.userItemMetaDataTransformationStep == null) {
            throw BadRequestException(ErrorCode(ErrorCodes.REQUEST_BODY_VIOLATION_ERROR_CODE.first, ErrorCodes.REQUEST_BODY_VIOLATION_ERROR_CODE.second, arrayListOf("Empty request body")))
        }
        if ((itemTitle != null && itemTitle.trim().isBlank())) {
            throw BadRequestException(ErrorCode(ErrorCodes.REQUEST_BODY_VIOLATION_ERROR_CODE.first, ErrorCodes.REQUEST_BODY_VIOLATION_ERROR_CODE.second, arrayListOf("Empty itemTitle")))
        }

        // Item Type can only be updated from a GENERIC to TCIN type, so the update request can only have ItemType.TCIN as part
        // of the request, implying change in type to TCIN. If the existing item that is being updated is not of type GENERIC
        // we throw an exception.
        if (itemType != null && itemType != ItemType.TCIN) {
            throw BadRequestException(ErrorCode(ErrorCodes.REQUEST_BODY_VIOLATION_ERROR_CODE.first, ErrorCodes.REQUEST_BODY_VIOLATION_ERROR_CODE.second, arrayListOf("Invalid Item type, Item can only be updated from GENERIC to TCIN type ")))
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

    fun updateRefId(itemType: ItemType): String? {
        // updateRefId is called to get the updated itemRefId in case when the attributes responsible for building the
        // item refId are being updated as part of ListItemUpdateRequestTO.
        // ItemRefId is calculated in the app layer and that could comprise of some of the attributes in the ListItemUpdateRequestTO,
        // so updating those attributes will also cause change in the itemRefId.
        // So refIdValidator interface is a required field in ListItemUpdateRequestTO, based on which we determine if
        // the itemRefId is supposed to be updated or not.
        return refIdValidator.populateRefIdIfRequired(itemType, this)
    }
}
