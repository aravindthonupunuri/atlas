package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.domain.DeduplicationManager
import com.tgt.lists.atlas.api.domain.DeleteListItemsManager
import com.tgt.lists.atlas.api.domain.UpdateListItemManager
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.ListItemResponseTO
import com.tgt.lists.atlas.api.transport.ListItemUpdateRequestTO
import com.tgt.lists.atlas.api.transport.mapper.ListItemMapper.Companion.toListItemResponseTO
import com.tgt.lists.atlas.api.transport.mapper.ListItemMapper.Companion.toUpdateListItemEntity
import com.tgt.lists.atlas.api.type.LIST_ITEM_STATE
import com.tgt.lists.atlas.api.type.UserMetaData.Companion.toUserMetaData
import com.tgt.lists.atlas.api.util.ErrorCodes.LIST_ITEM_NOT_FOUND_ERROR_CODE
import com.tgt.lists.common.components.exception.BadRequestException
import com.tgt.lists.common.components.exception.ErrorCode
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateListItemService(
    @Inject private val listRepository: ListRepository,
    @Inject private val updateListItemManager: UpdateListItemManager,
    @Inject private val deleteListItemsManager: DeleteListItemsManager,
    @Inject private val deduplicationManager: DeduplicationManager
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

        return listRepository.findListItemsByListId(listId).collectList().flatMap { existingListItems ->
            val existingItemToUpdate = existingListItems.firstOrNull { it.itemId == listItemId }

            if (existingItemToUpdate == null) {
                throw BadRequestException(ErrorCode(LIST_ITEM_NOT_FOUND_ERROR_CODE.first, LIST_ITEM_NOT_FOUND_ERROR_CODE.second, listOf("Item: $listItemId not found in listId: $listId")))
            } else {
                val existingUserItemMetadata = toUserMetaData(existingItemToUpdate.itemMetadata)
                val listItemUpdateRequestWithMergedMetadata = if (existingUserItemMetadata != null) {
                    listItemUpdateRequest.userItemMetaDataTransformationStep.let {
                        it.execute(existingUserItemMetadata).map { listItemUpdateRequest.copy(metadata = it) }
                    }
                } else {
                    Mono.just(listItemUpdateRequest)
                }
                listItemUpdateRequestWithMergedMetadata.flatMap {
                    updateItem(guestId, listId, it, existingItemToUpdate, existingListItems)
                }
            }
        }
    }

    private fun updateItem(
        guestId: String,
        listId: UUID,
        listItemUpdateRequest: ListItemUpdateRequestTO,
        existingItemToUpdate: ListItemEntity,
        existingListItems: List<ListItemEntity>
    ): Mono<ListItemResponseTO> {

        val itemToUpdate = toUpdateListItemEntity(existingItemToUpdate, listItemUpdateRequest)
        return deduplicationManager.updateDuplicateItems(
                guestId = guestId,
                listId = listId,
                items = listOf(itemToUpdate),
                existingItems = existingListItems.filter {
                    (it.itemId != itemToUpdate.itemId) && // Filtering out the item being updated
                            (it.itemState == itemToUpdate.itemState) // Filtering existing items which are in the same state as the items that are being updated, to check for duplicates.
                },
                itemState = LIST_ITEM_STATE.values().first { it.value == itemToUpdate.itemState!! }
        ).flatMap { updatedItems ->
            if (updatedItems.isNullOrEmpty()) {
                updateListItemManager.updateListItem(guestId, listId, itemToUpdate, existingItemToUpdate)
            } else {
                // If the item was deduped and also its state was changed, then delete the existing item in previous state.
                if (existingItemToUpdate.itemState != itemToUpdate.itemState) {
                    deleteListItemsManager.deleteListItems(guestId, listId, listOf(existingItemToUpdate)).map { updatedItems.first() }
                } else {
                    Mono.just(updatedItems.first())
                }
            }
        }.map { toListItemResponseTO(it) }
    }
}
