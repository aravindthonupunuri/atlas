package com.tgt.lists.atlas.api.transport.mapper

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.transport.*
import com.tgt.lists.atlas.api.util.*

class ListMapper {
    companion object {

        val mapper = jacksonObjectMapper()

        fun toNewListEntity(guestId: String, listRequestTO: ListRequestTO, listType: String, listSubtype: String? = null, defaultList: Boolean, testList: Boolean, expirationDays: Long): Pair<ListEntity, MetadataMap>  {

            val metadata = setMetadataMapFromList(tenantMetaData = listRequestTO.metadata)

            val now = getLocalInstant()

            val listEntity = ListEntity(id = Uuids.timeBased(),
                    guestId = guestId,
                    type = listType,
                    subtype = listSubtype,
                    title = listRequestTO.listTitle,
                    channel = listRequestTO.channel,
                    marker = if (defaultList) LIST_MARKER.D.name else null,
                    description = listRequestTO.shortDescription,
                    location = listRequestTO.locationId.toString(),
                    agentId = listRequestTO.agentId,
                    metadata = mapper.writeValueAsString(metadata),
                    state = LIST_STATE.A.name,
                    expiration = getExpirationDate(now, expirationDays),
                    createdAt = now,
                    updatedAt = now,
                    testList = testList)

            return Pair(listEntity, metadata)
        }

        fun setMetadataMapFromList(
                tenantMetaData: Map<String, Any>? = null
        ): MetadataMap {
            val metadata = mutableMapOf<String, Any>()

            // Push un-mapped list to cart attributes into cart meta data
            val listMetaData = ListMetaDataTO()

            val tenantUserMetaData = UserMetaDataTO(
                    userMetaData = tenantMetaData
            )

            metadata[Constants.LIST_METADATA] = mapper.writeValueAsString(listMetaData)
            metadata[Constants.USER_METADATA] = mapper.writeValueAsString(tenantUserMetaData)
            return metadata
        }

        fun getListMetaDataFromMetadataMap(metadataMap: MetadataMap?): ListMetaDataTO {
            var metadata: ListMetaDataTO? = mapper.readValue<ListMetaDataTO>((metadataMap?.get(Constants.LIST_METADATA) as? String).toString())
            if (metadata == null) {
                metadata = ListMetaDataTO(listStatus = null)
            }
            return metadata
        }

        fun getUserMetaDataFromMetadataMap(metadataMap: MetadataMap?): UserMetaDataTO? {
            var metadata: UserMetaDataTO? = mapper.readValue<UserMetaDataTO>((metadataMap?.get(Constants.USER_METADATA) as? String).toString())
            if (metadata == null) {
                metadata = UserMetaDataTO()
            }
            return metadata
        }

        fun toListResponseTO(
                listEntity: ListEntity,
                metadataMap: MetadataMap,
                pendingListItems: List<ListItemResponseTO>? = null,
                completedListItems: List<ListItemResponseTO>? = null,
                maxPendingItemCount: Int? = 0,
                maxCompletedItemsCount: Int? = 0,
                maxPendingPageCount: Int? = 0,
                maxCompletedPageCount: Int? = 0
        ): ListResponseTO {

            val listMetadata = getListMetaDataFromMetadataMap(metadataMap)
            val userMetadata = getUserMetaDataFromMetadataMap(metadataMap)

            return ListResponseTO(
                    listId = listEntity.id,
                    channel = listEntity.channel,
                    listType = listEntity.type,
                    defaultList = (listEntity.marker == LIST_MARKER.D.name),
                    listTitle = listEntity.title,
                    shortDescription = listEntity.description,
                    agentId = listEntity.agentId,
                    addedTs = getLocalDateTimeFromInstant(listEntity.createdAt),
                    lastModifiedTs = getLocalDateTimeFromInstant(listEntity.updatedAt),
                    metadata = userMetadata?.userMetaData,
                    pendingListItems = pendingListItems,
                    completedListItems = completedListItems,
                    maxPendingItemsCount = maxPendingItemCount,
                    maxCompletedItemsCount = maxCompletedItemsCount,
                    maxPendingPageCount = maxPendingPageCount,
                    maxCompletedPageCount = maxCompletedPageCount
            )
        }
    }
}