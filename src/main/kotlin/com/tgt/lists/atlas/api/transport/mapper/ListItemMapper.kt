package com.tgt.lists.atlas.api.transport.mapper

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.transport.ListItemRequestTO
import com.tgt.lists.atlas.api.transport.ListItemResponseTO
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
                    itemState = LIST_ITEM_STATE.PENDING.name,
                    itemId = Uuids.timeBased(),
                    itemRefId = listItemRequestTO.itemRefId,
                    itemType = listItemRequestTO.itemType.name,
                    itemTcin = listItemRequestTO.tcin,
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

        private fun getUserItemMetaDataFromMetadataMap(userItemMetaData: String?): UserItemMetaDataTO? {
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
                    itemType = ItemType.valueOf(listItemEntity.itemType!!),
                    relationshipType = null,
                    itemState = LIST_ITEM_STATE.valueOf(listItemEntity.itemState!!),
                    addedTs = listItemEntity.itemCreatedAt.toString(),
                    lastModifiedTs = listItemEntity.itemUpdatedAt.toString()
            )
        }
    }
}