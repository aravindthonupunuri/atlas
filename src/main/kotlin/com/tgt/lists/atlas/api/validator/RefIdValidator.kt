package com.tgt.lists.atlas.api.validator

import com.tgt.lists.atlas.api.transport.ListItemUpdateRequestTO
import com.tgt.lists.atlas.api.type.ItemType

// populateRefIdIfRequired is called to get the updated itemRefId in case when the attributes responsible for building the
// item refId are being updated as part of ListItemUpdateRequestTO.
// ItemRefId is calculated in the app layer and that could comprise of some of the attributes in the ListItemUpdateRequestTO,
// so updating those attributes will also cause change in the itemRefId.
// So refIdValidator interface is a required field in ListItemUpdateRequestTO, based on which we determine if
// the itemRefId is supposed to be updated or not.
interface RefIdValidator {
    fun populateRefIdIfRequired(itemType: ItemType, listItemUpdateRequestTO: ListItemUpdateRequestTO): String?
}