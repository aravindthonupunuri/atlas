package com.tgt.lists.atlas.api.transport.mapper

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListItemExtEntity
import com.tgt.lists.atlas.api.transport.ListItemRequestTO
import com.tgt.lists.atlas.api.transport.ListItemResponseTO
import com.tgt.lists.atlas.api.transport.ListItemUpdateRequestTO
import com.tgt.lists.atlas.api.transport.UserItemMetaDataTO
import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.api.util.UnitOfMeasure
import com.tgt.lists.atlas.api.util.getLocalInstant
import java.util.*

class ListItemMapper {
    companion object {

        val mapper = jacksonObjectMapper()

        fun toNewListItemEntity(
            listId: UUID,
            listItemRequestTO: ListItemRequestTO
        ): ListItemEntity {

            val now = getLocalInstant()

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
                    itemSubchannel = null,
                    itemMetadata = mapper.writeValueAsString(listItemRequestTO.metadata),
                    itemNotes = listItemRequestTO.itemNote,
                    itemQty = null,
                    itemQtyUom = null,
                    itemReqQty = listItemRequestTO.requestedQuantity,
                    itemCreatedAt = now,
                    itemUpdatedAt = now)
        }

        fun toUpdateListItemEntity(
            existingListItemEntity: ListItemEntity,
            listItemUpdateRequestTO: ListItemUpdateRequestTO
        ): ListItemEntity {

            return ListItemEntity(
                    id = existingListItemEntity.id,
                    itemState = listItemUpdateRequestTO.itemState?.value ?: existingListItemEntity.itemState,
                    itemId = existingListItemEntity.itemId,
                    itemRefId = existingListItemEntity.itemRefId,
                    itemType = existingListItemEntity.itemType,
                    itemTcin = existingListItemEntity.itemTcin,
                    itemTitle = listItemUpdateRequestTO.itemTitle,
                    itemDpci = existingListItemEntity.itemDpci,
                    itemBarcode = existingListItemEntity.itemBarcode,
                    itemDesc = existingListItemEntity.itemDesc,
                    itemChannel = existingListItemEntity.itemChannel,
                    itemSubchannel = existingListItemEntity.itemSubchannel,
                    itemMetadata = listItemUpdateRequestTO.metadata.let { mapper.writeValueAsString(listItemUpdateRequestTO.metadata) } ?: existingListItemEntity.itemMetadata,
                    itemNotes = listItemUpdateRequestTO.itemNote ?: existingListItemEntity.itemNotes,
                    itemQty = existingListItemEntity.itemQty,
                    itemQtyUom = existingListItemEntity.itemQtyUom,
                    itemReqQty = listItemUpdateRequestTO.requestedQuantity ?: existingListItemEntity.itemReqQty,
                    itemCreatedAt = existingListItemEntity.itemCreatedAt,
                    itemUpdatedAt = existingListItemEntity.itemUpdatedAt)
        }

        fun getUserItemMetaDataFromMetadataMap(userItemMetaData: String?): UserItemMetaDataTO? {
            var metadata: UserItemMetaDataTO? = userItemMetaData?.let { mapper.readValue<UserItemMetaDataTO>(it) }
            if (metadata == null) {
                metadata = UserItemMetaDataTO()
            }
            return metadata
        }

        fun toListItemResponseTO(
            listItemEntity: ListItemEntity
        ): ListItemResponseTO {

            return ListItemResponseTO(
                    listItemId = listItemEntity.itemId,
                    itemRefId = listItemEntity.itemRefId!!,
                    channel = listItemEntity.itemChannel,
                    tcin = listItemEntity.itemTcin,
                    itemTitle = listItemEntity.itemTitle ?: listItemEntity.itemDesc,
                    requestedQuantity = listItemEntity.itemReqQty,
                    unitOfMeasure = listItemEntity.itemQtyUom?.let { UnitOfMeasure.valueOf(it) },
                    itemNote = listItemEntity.itemNotes,
                    price = null,
                    listPrice = null,
                    offerCount = 0,
                    images = null,
                    metadata = getUserItemMetaDataFromMetadataMap(listItemEntity.itemMetadata)?.userMetaData,
                    itemType = ItemType.values().first { it.value == listItemEntity.itemType!! },
                    relationshipType = null,
                    itemState = LIST_ITEM_STATE.values().first { it.value == listItemEntity.itemState!! },
                    addedTs = listItemEntity.itemCreatedAt.toString(),
                    lastModifiedTs = listItemEntity.itemUpdatedAt.toString()
            )
        }

        fun toListItemResponseTO(
            listItemExtEntity: ListItemExtEntity
        ): ListItemResponseTO {

            return ListItemResponseTO(
                    listItemId = listItemExtEntity.itemId,
                    itemRefId = listItemExtEntity.itemRefId!!,
                    channel = listItemExtEntity.itemChannel,
                    tcin = listItemExtEntity.itemTcin,
                    itemTitle = listItemExtEntity.itemTitle ?: listItemExtEntity.itemDesc,
                    requestedQuantity = listItemExtEntity.itemReqQty,
                    unitOfMeasure = listItemExtEntity.itemQtyUom?.let { UnitOfMeasure.valueOf(it) },
                    itemNote = listItemExtEntity.itemNotes,
                    price = null,
                    listPrice = null,
                    offerCount = 0,
                    images = null,
                    metadata = getUserItemMetaDataFromMetadataMap(listItemExtEntity.itemMetadata)?.userMetaData,
                    itemType = ItemType.values().first { it.value == listItemExtEntity.itemType!! },
                    relationshipType = null,
                    itemState = LIST_ITEM_STATE.values().first { it.value == listItemExtEntity.itemState!! },
                    addedTs = listItemExtEntity.itemCreatedAt.toString(),
                    lastModifiedTs = listItemExtEntity.itemUpdatedAt.toString()
            )
        }
    }
}