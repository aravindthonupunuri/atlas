package com.tgt.lists.atlas.util

import com.tgt.lists.atlas.api.domain.model.entity.*
import com.tgt.lists.atlas.api.transport.ListGetAllResponseTO
import com.tgt.lists.atlas.api.transport.ListItemResponseTO
import com.tgt.lists.atlas.api.util.ItemRefIdBuilder
import com.tgt.lists.atlas.api.util.ItemType
import java.time.Instant
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

    fun createListItemEntity(listId: UUID, itemId: UUID, itemState: String, itemType: String, itemRefId: String, tcin: String?, itemTitle: String?, itemReqQty: Int?, itemNotes: String?): ListItemEntity {
        return createListItemEntity(listId, itemId, itemState, itemType, itemRefId, tcin, itemTitle, itemReqQty, itemNotes, null, null)
    }

    fun createListItemEntity(listId: UUID, itemId: UUID, itemState: String, itemType: String, itemRefId: String, tcin: String?, itemTitle: String?, itemReqQty: Int?, itemNotes: String?, itemCreatedDate: Instant?, itemUpdatedDate: Instant?): ListItemEntity {
        return ListItemEntity(id = listId, itemId = itemId, itemState = itemState, itemType = itemType, itemRefId = itemRefId, itemTcin = tcin, itemTitle = itemTitle, itemReqQty = itemReqQty, itemNotes = itemNotes, itemCreatedAt = itemCreatedDate, itemUpdatedAt = itemUpdatedDate)
    }

    fun createGuestPreferenceEntity(guestId: String, listSortOrder: String?): GuestPreferenceEntity {
        return GuestPreferenceEntity(guestId = guestId, listSortOrder = listSortOrder)
    }

    fun createListPreferenceEntity(listId: UUID, guestId: String, itemSortOrder: String?): ListPreferenceEntity {
        return ListPreferenceEntity(listId = listId, guestId = guestId, itemSortOrder = itemSortOrder)
    }

    fun getListPreferenceEntity(listId: UUID, guestId: String): ListPreferenceEntity {
        return ListPreferenceEntity(listId = listId, guestId = guestId)
    }

    fun getTenantRefId(itemType: ItemType, id: String): String {
        return ItemRefIdBuilder.buildItemRefId(itemType, id)
    }

    fun createListItemExtEntity(listEntity: ListEntity, listItemEntity: ListItemEntity): ListItemExtEntity {
        return ListItemExtEntity(id = listEntity.id, itemState = listItemEntity.itemState, itemId = listItemEntity.itemId,
                itemType = listItemEntity.itemType, title = listEntity.title, type = listEntity.type, subtype = listEntity.subtype,
        guestId = listEntity.guestId, marker = listEntity.marker, itemRefId = listItemEntity.itemRefId, itemTcin = listItemEntity.itemTcin,
        description = listEntity.description, itemDesc = listItemEntity.itemDesc, itemTitle = listItemEntity.itemTitle,
        itemCreatedAt = listItemEntity.itemCreatedAt, itemUpdatedAt = listItemEntity.itemUpdatedAt)
    }
}