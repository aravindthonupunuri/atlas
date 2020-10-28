package com.tgt.lists.atlas.api.validator

import com.tgt.lists.atlas.api.transport.ListItemUpdateRequestTO

/**
 * RefIdValidator is used to figure out if the update request also requires itemRefId as part of the request.
 * itemRefId is calculated in the app layer and that could comprise of some of the attributes in the ListItemUpdateRequestTO,
 * so updating just those attributes will also cuase change in the itemRefId.
 * Like for example if we are trying to update tcin value for a TCIN item, then the request ListItemUpdateRequestTO
 * should also have itemRefId as part of ListItemUpdateRequestTO since tcin value is used to calculate the itemRefId
 * So refIdValidator interface is a required field in ListItemUpdateRequestTO, based on which we determine if
 * itemRefId is supposed to be part of ListItemUpdateRequestTO or not.
 */
interface RefIdValidator {
    fun requireRefId(listItemUpdateRequestTO: ListItemUpdateRequestTO): Boolean
}