package com.tgt.lists.atlas.util

import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.transport.ListGetAllResponseTO
import com.tgt.lists.atlas.api.transport.ListItemResponseTO
import java.util.*

class ListDataProvider {
    fun getList(listId: UUID, completedListId: UUID? = null, listTitle: String): ListGetAllResponseTO {
        return ListGetAllResponseTO(listId = listId, completedListId = completedListId, listTitle = listTitle, listType = "SHOPPING", shortDescription = "test", metadata = null)
    }

    fun getListItem(listItemId: UUID, itemTitle: String): ListItemResponseTO {
        return ListItemResponseTO(listItemId = listItemId, itemTitle = itemTitle, itemRefId = "" + itemTitle.hashCode())
    }

    fun createListEntity(listId: UUID, listTitle: String, listType: String, listSubtype: String, guestId: String, listMarker: String): ListEntity {
        return ListEntity(id = listId, title = listTitle, type = listType, subtype = listSubtype, guestId = guestId, marker = listMarker)
    }

    fun createListItemEntity(listId: UUID, itemId: UUID, itemState: String, itemType: String, itemRefId: String): ListItemEntity {
        return ListItemEntity(id = listId, itemId = itemId, itemState = itemState, itemType = itemType, itemRefId = itemRefId)
    }
}