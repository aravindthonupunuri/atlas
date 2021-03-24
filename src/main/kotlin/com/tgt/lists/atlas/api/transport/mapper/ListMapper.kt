package com.tgt.lists.atlas.api.transport.mapper

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListItemExtEntity
import com.tgt.lists.atlas.api.transport.*
import com.tgt.lists.atlas.api.type.LIST_MARKER
import com.tgt.lists.atlas.api.type.LIST_STATE
import com.tgt.lists.atlas.api.type.UserMetaData
import com.tgt.lists.atlas.api.type.UserMetaData.Companion.toEntityMetadata
import com.tgt.lists.atlas.api.type.UserMetaData.Companion.toUserMetaData
import com.tgt.lists.atlas.api.util.getLocalDateTimeFromInstant
import com.tgt.lists.atlas.api.util.getLocalInstant
import java.time.Instant
import java.time.LocalDate
import java.util.*

class ListMapper {
    companion object {
        fun toNewListEntity(
            guestId: String,
            listRequestTO: ListRequestTO,
            listType: String,
            listSubtype: String,
            defaultList: Boolean,
            testList: Boolean,
            expiration: LocalDate,
            listId: UUID? = Uuids.timeBased(),
            createdAt: Instant? = null,
            updatedAt: Instant? = null
        ): ListEntity {
            // Do not set created or updated time in here, set it in the repository instead
            return ListEntity(
                    id = listId,
                    guestId = guestId,
                    type = listType,
                    subtype = listSubtype,
                    title = listRequestTO.listTitle,
                    channel = listRequestTO.channel,
                    subchannel = listRequestTO.subChannel,
                    marker = if (defaultList) LIST_MARKER.DEFAULT.value else "",
                    description = listRequestTO.shortDescription,
                    location = listRequestTO.locationId.toString(),
                    agentId = listRequestTO.agentId,
                    metadata = toEntityMetadata(listRequestTO.metadata),
                    state = listRequestTO.listState.value, // Should be set by the app layer
                    expiration = expiration,
                    testList = testList,
                    createdAt = createdAt,
                    updatedAt = updatedAt
            )
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

        fun toUpdateListEntity(existingEntity: ListEntity, updatedMetaData: UserMetaData?, listUpdateRequestTO: ListUpdateRequestTO): Pair<ListEntity, ListEntity> {
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
                    metadata = toEntityMetadata(updatedMetaData ?: listUpdateRequestTO.metadata),
                    expiration = listUpdateRequestTO.expiration ?: existingEntity.expiration)
            )
        }

        fun toListResponseTO(
            listEntity: ListEntity,
            pendingListItems: List<ListItemResponseTO>? = null,
            completedListItems: List<ListItemResponseTO>? = null,
            maxPendingItemsCount: Int? = 0,
            maxCompletedItemsCount: Int? = 0,
            maxPendingPageCount: Int? = 0,
            maxCompletedPageCount: Int? = 0
        ): ListResponseTO {

            return ListResponseTO(
                    listId = listEntity.id,
                    guestId = listEntity.guestId,
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
                    metadata = toUserMetaData(listEntity.metadata),
                    pendingListItems = pendingListItems,
                    completedListItems = completedListItems,
                    maxPendingItemsCount = maxPendingItemsCount,
                    maxCompletedItemsCount = maxCompletedItemsCount,
                    maxPendingPageCount = maxPendingPageCount,
                    maxCompletedPageCount = maxCompletedPageCount,
                    expiration = listEntity.expiration
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
                    metadata = toUserMetaData(listEntity.metadata),
                    defaultList = (listEntity.marker == LIST_MARKER.DEFAULT.value),
                    maxListsCount = maxListsCount!!,
                    addedTs = getLocalDateTimeFromInstant(listEntity.createdAt),
                    lastModifiedTs = getLocalDateTimeFromInstant(listEntity.updatedAt)
            )
        }
    }
}
