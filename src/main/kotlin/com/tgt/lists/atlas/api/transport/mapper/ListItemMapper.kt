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
import com.tgt.lists.atlas.api.util.*
import java.util.*

class ListItemMapper {
    companion object {

        val mapper = jacksonObjectMapper()

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
                    itemMetadata = mapper.writeValueAsString(setItemMetadataMapFromList(listItemRequestTO.metadata)),
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
                    itemMetadata = listItemUpdateRequestTO.metadata.let {
                        mapper.writeValueAsString(setItemMetadataMapFromList(listItemUpdateRequestTO.metadata))
                    } ?: existingListItemEntity.itemMetadata,
                    itemNotes = listItemUpdateRequestTO.itemNote ?: existingListItemEntity.itemNotes,
                    itemQty = listItemUpdateRequestTO.fulfilledQuantity ?: existingListItemEntity.itemQty,
                    itemQtyUom = existingListItemEntity.itemQtyUom,
                    itemReqQty = listItemUpdateRequestTO.requestedQuantity ?: existingListItemEntity.itemReqQty,
                    itemCreatedAt = existingListItemEntity.itemCreatedAt,
                    itemUpdatedAt = existingListItemEntity.itemUpdatedAt)
        }

        fun setItemMetadataMapFromList(tenantItemMetaData: Map<String, Any>? = null): MetadataMap {
            val metadata = mutableMapOf<String, Any>()

            val tenantItemUserMetaData = UserItemMetaDataTO(
                    userMetaData = tenantItemMetaData
            )

            metadata[Constants.USER_ITEM_METADATA] = ListMapper.mapper.writeValueAsString(tenantItemUserMetaData)
            return metadata
        }

        fun getUserItemMetaDataFromMetadataMap(userItemMetaData: String?): UserItemMetaDataTO? {
            return UserItemMetaDataTO(userItemMetaData?.let { mapper.readValue<Map<String, Any>>(it) })
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
                    metadata = getUserItemMetaDataFromMetadataMap(listItemEntity.itemMetadata)?.userMetaData,
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
                    metadata = getUserItemMetaDataFromMetadataMap(listItemExtEntity.itemMetadata)?.userMetaData,
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
                    addedTs = listItemExtEntity.itemCreatedAt.toString(),
                    lastModifiedTs = listItemExtEntity.itemUpdatedAt.toString()
            )
        }
    }
}