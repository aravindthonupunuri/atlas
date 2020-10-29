package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.domain.UpdateListItemManager
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.ListItemResponseTO
import com.tgt.lists.atlas.api.transport.ListItemUpdateRequestTO
import com.tgt.lists.atlas.api.transport.mapper.ListItemMapper.Companion.getUserItemMetaDataFromMetadataMap
import com.tgt.lists.atlas.api.transport.mapper.ListItemMapper.Companion.toListItemResponseTO
import com.tgt.lists.atlas.api.transport.mapper.ListItemMapper.Companion.toUpdateListItemEntity
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import mu.KotlinLogging
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateListItemService(
    @Inject private val listRepository: ListRepository,
    @Inject private val updateListItemManager: UpdateListItemManager
) {

    private val logger = KotlinLogging.logger {}

    /**
     *
     * The method implements the functionality to update list item
     *
     */
    fun updateListItem(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        locationId: Long,
        listId: UUID,
        listItemId: UUID,
        listItemUpdateRequest: ListItemUpdateRequestTO
    ): Mono<ListItemResponseTO> {

        logger.debug("[updateListItem] guestId: $guestId, listId: $listId, listItemId: $listItemId, locationId: $locationId")

        return findItemInList(listId, listItemId).flatMap { existingListItemEntity ->
            val existingUserItemMetadata = getUserItemMetaDataFromMetadataMap(existingListItemEntity.itemMetadata) // Get user item metaData
            if (existingUserItemMetadata != null && listItemUpdateRequest.userItemMetaDataTransformationStep != null) {
                listItemUpdateRequest.userItemMetaDataTransformationStep.execute(existingUserItemMetadata)
                        .map { listItemUpdateRequest.copy(metadata = it.userMetaData) }
            } else {
                Mono.just(listItemUpdateRequest)
            }.flatMap {
                updateListItemManager.updateListItem(guestId, listId, toUpdateListItemEntity(existingListItemEntity, it), existingListItemEntity)
            }
        }.map { toListItemResponseTO(it) }
    }

    private fun findItemInList(
        listId: UUID,
        listItemId: UUID
    ): Mono<ListItemEntity> {
        return listRepository.findListItemByItemId(listId, LIST_ITEM_STATE.PENDING.value, listItemId)
                .switchIfEmpty {
                    logger.debug { "Item is not found in pending state, check for item in completed state" }
                    listRepository.findListItemByItemId(listId, LIST_ITEM_STATE.COMPLETED.value, listItemId)
                }
    }
}
