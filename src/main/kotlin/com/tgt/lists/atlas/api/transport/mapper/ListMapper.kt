package com.tgt.lists.atlas.api.transport.mapper

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListItemExtEntity
import com.tgt.lists.atlas.api.transport.*
import com.tgt.lists.atlas.api.util.*

class ListMapper {
    companion object {

        val mapper = jacksonObjectMapper()

        fun toNewListEntity(
            guestId: String,
            listRequestTO: ListRequestTO,
            listType: String,
            listSubtype: String,
            defaultList: Boolean,
            testList: Boolean,
            expirationDays: Long
        ): ListEntity {

            // Do not set created or updated time in here, set it in the repository instead
            return ListEntity(
                    id = Uuids.timeBased(),
                    guestId = guestId,
                    type = listType,
                    subtype = listSubtype,
                    title = listRequestTO.listTitle,
                    channel = listRequestTO.channel,
                    subchannel = listRequestTO.subChannel,
                    marker = if (defaultList) LIST_MARKER.DEFAULT.value else null,
                    description = listRequestTO.shortDescription,
                    location = listRequestTO.locationId.toString(),
                    agentId = listRequestTO.agentId,
                    metadata = mapper.writeValueAsString(setMetadataMapFromList(listRequestTO.metadata)),
                    state = listRequestTO.listState.value, // Should be set by the app layer
                    expiration = getExpirationDate(getLocalInstant(), expirationDays),
                    testList = testList)
        }

        fun toListEntity(
            listItemExtEntity: ListItemExtEntity
        ): ListEntity {
            return ListEntity(
                    id = listItemExtEntity.id,
                    guestId = listItemExtEntity.guestId,
                    type = listItemExtEntity.type,
                    subtype = listItemExtEntity.subtype,
                    title = listItemExtEntity.title,
                    channel = listItemExtEntity.channel,
                    subchannel = listItemExtEntity.subchannel,
                    marker = listItemExtEntity.marker,
                    description = listItemExtEntity.description,
                    location = listItemExtEntity.location,
                    agentId = listItemExtEntity.agentId,
                    metadata = listItemExtEntity.metadata,
                    state = listItemExtEntity.state,
                    expiration = listItemExtEntity.expiration,
                    createdAt = listItemExtEntity.createdAt,
                    updatedAt = listItemExtEntity.itemUpdatedAt,
                    testList = listItemExtEntity.testList)
        }

        fun toUpdateListEntity(existingEntity: ListEntity, updatedMetaData: UserMetaDataTO?, listUpdateRequestTO: ListUpdateRequestTO): Pair<ListEntity, ListEntity> {
            // TODO revisit this once all the attributes are set
            return Pair(existingEntity, existingEntity.copy(
                    title = listUpdateRequestTO.listTitle ?: existingEntity.title,
                    description = listUpdateRequestTO.shortDescription ?: existingEntity.description,
                    marker = if (listUpdateRequestTO.defaultList != null && listUpdateRequestTO.defaultList) LIST_MARKER.DEFAULT.value
                    else existingEntity.marker,
                    notes = listUpdateRequestTO.shortDescription ?: existingEntity.notes,
                    state = if (listUpdateRequestTO.listState != null) listUpdateRequestTO.listState.value
                    else existingEntity.state,
                    updatedAt = getLocalInstant(),
                    metadata = mapper.writeValueAsString(setMetadataMapFromList(updatedMetaData?.userMetaData))))
        }

        // TODO Revisit this - do we really need this level of node map?
        // eg: metadata={user_metadata={"user_meta_data":{"registry-metadata": null}}}
        fun setMetadataMapFromList(tenantMetaData: Map<String, Any>? = null): MetadataMap {
            val metadata = mutableMapOf<String, Any>()

            val tenantUserMetaData = UserMetaDataTO(
                    userMetaData = tenantMetaData
            )

            metadata[Constants.USER_METADATA] = mapper.writeValueAsString(tenantUserMetaData)
            return metadata
        }

        fun getUserMetaDataFromMetadataMap(metadataMap: MetadataMap?): UserMetaDataTO? {
            var metadata: UserMetaDataTO? = mapper.readValue<UserMetaDataTO>((metadataMap?.get(Constants.USER_METADATA) as? String).toString())
            if (metadata == null) {
                metadata = UserMetaDataTO()
            }
            return metadata
        }

        fun getUserMetaDataFromMetadataMap(userMetaData: String?): UserMetaDataTO? {
            return UserMetaDataTO(userMetaData?.let { mapper.readValue<Map<String, Any>>(it) })
        }

        fun toListResponseTO(
            listEntity: ListEntity,
            pendingListItems: List<ListItemResponseTO>? = null,
            completedListItems: List<ListItemResponseTO>? = null,
            maxPendingItemCount: Int? = 0,
            maxCompletedItemsCount: Int? = 0,
            maxPendingPageCount: Int? = 0,
            maxCompletedPageCount: Int? = 0
        ): ListResponseTO {

            return ListResponseTO(
                    listId = listEntity.id,
                    channel = listEntity.channel,
                    subChannel = listEntity.subchannel,
                    listType = listEntity.type,
                    listSubType = listEntity.subtype,
                    listState =
                    if (listEntity.state != null)
                        LIST_STATE.values().first { listState -> listState.value == listEntity.state!! }
                    else LIST_STATE.INACTIVE,
                    defaultList = (listEntity.marker == LIST_MARKER.DEFAULT.value),
                    listTitle = listEntity.title,
                    shortDescription = listEntity.description,
                    agentId = listEntity.agentId,
                    addedTs = getLocalDateTimeFromInstant(listEntity.createdAt),
                    lastModifiedTs = getLocalDateTimeFromInstant(listEntity.updatedAt),
                    metadata = getUserMetaDataFromMetadataMap(listEntity.metadata)?.userMetaData,
                    pendingListItems = pendingListItems,
                    completedListItems = completedListItems,
                    maxPendingItemsCount = maxPendingItemCount,
                    maxCompletedItemsCount = maxCompletedItemsCount,
                    maxPendingPageCount = maxPendingPageCount,
                    maxCompletedPageCount = maxCompletedPageCount
            )
        }

        fun toListGetAllResponseTO(listEntity: ListEntity, maxListsCount: Int? = 50): ListGetAllResponseTO {
            return ListGetAllResponseTO(
                    listId = listEntity.id,
                    channel = listEntity.channel,
                    subChannel = listEntity.subchannel,
                    listType = listEntity.type,
                    listSubType = listEntity.subtype,
                    listState = if (listEntity.state != null)
                        LIST_STATE.values().first { listState -> listState.value == listEntity.state!! }
                    else LIST_STATE.INACTIVE,
                    listTitle = listEntity.title,
                    shortDescription = listEntity.description,
                    agentId = listEntity.agentId,
                    metadata = getUserMetaDataFromMetadataMap(listEntity.metadata)?.userMetaData,
                    defaultList = (listEntity.marker == LIST_MARKER.DEFAULT.value),
                    maxListsCount = maxListsCount!!,
                    addedTs = getLocalDateTimeFromInstant(listEntity.createdAt),
                    lastModifiedTs = getLocalDateTimeFromInstant(listEntity.updatedAt)
            )
        }
    }
}