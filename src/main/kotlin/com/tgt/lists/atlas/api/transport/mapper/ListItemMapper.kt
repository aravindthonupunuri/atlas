package com.tgt.lists.atlas.api.transport.mapper

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListItemExtEntity
import com.tgt.lists.atlas.api.transport.ListItemRequestTO
import com.tgt.lists.atlas.api.transport.ListItemResponseTO
import com.tgt.lists.atlas.api.transport.ListItemUpdateRequestTO
import com.tgt.lists.atlas.api.type.ItemType
import com.tgt.lists.atlas.api.type.LIST_ITEM_STATE
import com.tgt.lists.atlas.api.type.UnitOfMeasure
import com.tgt.lists.atlas.api.type.UserMetaData.Companion.toEntityMetadata
import com.tgt.lists.atlas.api.type.UserMetaData.Companion.toUserMetaData
import com.tgt.lists.atlas.api.util.getLocalDateTimeFromInstant
import java.util.*

class ListItemMapper {
    companion object {
        fun toNewListItemEntity(
            listId: UUID,
            listItemRequestTO: ListItemRequestTO
        ): ListItemEntity {

            // Do not set created or updated time in here, set it in the repository instead
            return ListItemEntity(
                    id = listId,
                    itemState = LIST_ITEM_STATE.PENDING.value,
                    itemId = Uuids.timeBased(),
                    itemRefId = listItemRequestTO.itemRefId,
                    itemType = listItemRequestTO.itemType.value,
                    itemTcin = listItemRequestTO.tcin?.trim(),
                    itemTitle = listItemRequestTO.itemTitle,
                    itemDpci = null,
                    itemBarcode = null,
                    itemDesc = null,
                    itemChannel = listItemRequestTO.channel,
                    itemSubchannel = listItemRequestTO.subChannel,
                    itemAgentId = listItemRequestTO.agentId,
                    itemMetadata = toEntityMetadata(listItemRequestTO.metadata),
                    itemNotes = listItemRequestTO.itemNote,
                    itemQty = listItemRequestTO.fulfilledQuantity,
                    itemQtyUom = null,
                    itemReqQty = listItemRequestTO.requestedQuantity)
        }

        fun toUpdateListItemEntity(
            existingListItemEntity: ListItemEntity,
            listItemUpdateRequestTO: ListItemUpdateRequestTO
        ): ListItemEntity {

            return ListItemEntity(
                    id = existingListItemEntity.id,
                    itemState = listItemUpdateRequestTO.itemState?.value ?: existingListItemEntity.itemState,
                    itemId = existingListItemEntity.itemId,
                    itemRefId = listItemUpdateRequestTO.updateRefId(ItemType.values().first { it.value == existingListItemEntity.itemType!! }) ?: existingListItemEntity.itemRefId,
                    itemType = listItemUpdateRequestTO.itemType?.value ?: existingListItemEntity.itemType,
                    itemTcin = listItemUpdateRequestTO.tcin ?: existingListItemEntity.itemTcin,
                    itemTitle = listItemUpdateRequestTO.itemTitle ?: existingListItemEntity.itemTitle,
                    itemDpci = existingListItemEntity.itemDpci,
                    itemBarcode = existingListItemEntity.itemBarcode,
                    itemDesc = existingListItemEntity.itemDesc,
                    itemChannel = existingListItemEntity.itemChannel,
                    itemSubchannel = existingListItemEntity.itemSubchannel,
                    itemMetadata = listItemUpdateRequestTO.metadata.let { toEntityMetadata(listItemUpdateRequestTO.metadata) } ?: existingListItemEntity.itemMetadata,
                    itemNotes = listItemUpdateRequestTO.itemNote ?: existingListItemEntity.itemNotes,
                    itemQty = listItemUpdateRequestTO.fulfilledQuantity ?: existingListItemEntity.itemQty,
                    itemQtyUom = existingListItemEntity.itemQtyUom,
                    itemReqQty = listItemUpdateRequestTO.requestedQuantity ?: existingListItemEntity.itemReqQty,
                    itemCreatedAt = existingListItemEntity.itemCreatedAt,
                    itemUpdatedAt = existingListItemEntity.itemUpdatedAt)
        }

        fun toListItemResponseTO(
            listItemEntity: ListItemEntity
        ): ListItemResponseTO {

            return ListItemResponseTO(
                    listItemId = listItemEntity.itemId,
                    itemRefId = listItemEntity.itemRefId!!,
                    channel = listItemEntity.itemChannel,
                    subChannel = listItemEntity.itemSubchannel,
                    tcin = listItemEntity.itemTcin,
                    itemTitle = listItemEntity.itemTitle ?: listItemEntity.itemDesc,
                    requestedQuantity = listItemEntity.itemReqQty,
                    fulfilledQuantity = listItemEntity.itemQty,
                    unitOfMeasure = listItemEntity.itemQtyUom?.let { UnitOfMeasure.valueOf(it) },
                    itemNote = listItemEntity.itemNotes,
                    price = null,
                    listPrice = null,
                    offerCount = 0,
                    metadata = toUserMetaData(listItemEntity.itemMetadata),
                    itemType =
                    if (listItemEntity.itemType != null)
                        ItemType.values().first { it.value == listItemEntity.itemType!! }
                    else null,
                    relationshipType = null,
                    itemState =
                    if (listItemEntity.itemState != null)
                        LIST_ITEM_STATE.values().first { it.value == listItemEntity.itemState!! }
                    else null,
                    agentId = listItemEntity.itemAgentId,
                    addedTs = getLocalDateTimeFromInstant(listItemEntity.itemCreatedAt),
                    lastModifiedTs = getLocalDateTimeFromInstant(listItemEntity.itemUpdatedAt)
            )
        }

        fun toListItemResponseTO(
            listItemExtEntity: ListItemExtEntity
        ): ListItemResponseTO {

            return ListItemResponseTO(
                    listItemId = listItemExtEntity.itemId,
                    itemRefId = listItemExtEntity.itemRefId!!,
                    channel = listItemExtEntity.itemChannel,
                    subChannel = listItemExtEntity.itemSubchannel,
                    tcin = listItemExtEntity.itemTcin,
                    itemTitle = listItemExtEntity.itemTitle ?: listItemExtEntity.itemDesc,
                    requestedQuantity = listItemExtEntity.itemReqQty,
                    fulfilledQuantity = listItemExtEntity.itemQty,
                    unitOfMeasure = listItemExtEntity.itemQtyUom?.let { UnitOfMeasure.valueOf(it) },
                    itemNote = listItemExtEntity.itemNotes,
                    price = null,
                    listPrice = null,
                    offerCount = 0,
                    metadata = toUserMetaData(listItemExtEntity.itemMetadata),
                    itemType =
                    if (listItemExtEntity.itemType != null)
                        ItemType.values().first { it.value == listItemExtEntity.itemType!! }
                    else null,
                    relationshipType = null,
                    itemState =
                    if (listItemExtEntity.itemState != null)
                        LIST_ITEM_STATE.values().first { it.value == listItemExtEntity.itemState!! }
                    else null,
                    agentId = listItemExtEntity.itemAgentId,
                    addedTs = getLocalDateTimeFromInstant(listItemExtEntity.itemCreatedAt),
                    lastModifiedTs = getLocalDateTimeFromInstant(listItemExtEntity.itemUpdatedAt)
            )
        }
    }
}
