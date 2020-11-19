package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.domain.DeduplicationManager
import com.tgt.lists.atlas.api.domain.DeleteListItemsManager
import com.tgt.lists.atlas.api.domain.UpdateListItemManager
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.ListItemsStateUpdateResponseTO
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import mu.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateListItemsStateService(
    @Inject private val listRepository: ListRepository,
    @Inject private val updateListItemManager: UpdateListItemManager,
    @Inject private val deleteListItemsManager: DeleteListItemsManager,
    @Inject private val deduplicationManager: DeduplicationManager
) {

    private val logger = KotlinLogging.logger {}

    /**
     *
     * The method implements the functionality to update multiple items state.
     *
     */
    fun updateListItemsState(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        locationId: Long,
        listId: UUID,
        listItemState: LIST_ITEM_STATE,
        itemIdsToUpdate: List<UUID>
    ): Mono<ListItemsStateUpdateResponseTO> {

        logger.debug("[updateMultipleListItemState] guestId: $guestId, listId: $listId, listItemState: $listItemState, itemIds: $itemIdsToUpdate, locationId: $locationId")

        return listRepository.findListItemsByListId(listId).collectList().flatMap {
            val triple = validateItems(
                    existingItems = it,
                    itemIdsToUpdate = itemIdsToUpdate,
                    listItemState = listItemState
            )
            updateState(guestId, listId, it, triple.first, listItemState).map { updatedItems ->
                val successItems = arrayListOf<UUID>()
                successItems.addAll(updatedItems.map { it.itemId!! })
                successItems.addAll(triple.second)
                ListItemsStateUpdateResponseTO(listId, successListItemIds = successItems, failedListItemIds = triple.third)
            }
        }
    }

    /**
     *
     * The method implements the functionality to validate the itemsIds to be updated.
     * itemsToUpdate: Items that can be updated into the specified item state
     * itemsAlreadyUpdated: Items that are already in the state specified in the request, so we do not process these ids
     * invalidItems: Items that are not part of the list
     *
     */
    private fun validateItems(
        existingItems: List<ListItemEntity>,
        itemIdsToUpdate: List<UUID>,
        listItemState: LIST_ITEM_STATE
    ): Triple<List<ListItemEntity>, List<UUID>, List<UUID>> {
        val itemsToUpdate = arrayListOf<ListItemEntity>()
        val itemsAlreadyUpdated = arrayListOf<UUID>()
        val invalidItems = arrayListOf<UUID>()
        itemIdsToUpdate.stream().forEach { itemId ->
                val item = existingItems.firstOrNull { it.itemId == itemId }
                if (item != null) {
                    val itemState = item.itemState!!
                    if (itemState == listItemState.value) {
                        itemsAlreadyUpdated.add(itemId)
                    } else {
                        itemsToUpdate.add(item)
                    }
                } else {
                    invalidItems.add(itemId)
                }
            }
            logger.debug("ItemsToUpdate: ${itemsToUpdate.map { it.itemId } }, ItemsAlreadyUpdated: $itemsAlreadyUpdated, InvalidItems: $invalidItems ")

            return Triple(itemsToUpdate, itemsAlreadyUpdated, invalidItems)
    }

    /**
     *
     * The method implements the functionality to publish CompletionItemActionEvent or PendingItemActionEvent
     * for processing item state change.
     *
     */
    private fun updateState(
        guestId: String,
        listId: UUID,
        existingItems: List<ListItemEntity>,
        itemsToUpdate: List<ListItemEntity>,
        listItemState: LIST_ITEM_STATE
    ): Mono<List<ListItemEntity>> {
        return if (itemsToUpdate.isNullOrEmpty()) {
            logger.debug("[updateState] No items to update")
            Mono.just(itemsToUpdate)
        } else {
            deduplicationManager.updateDuplicateItems(
                    guestId = guestId,
                    listId = listId,
                    items = itemsToUpdate,
                    existingItems = existingItems.filter { it.itemState == listItemState.value }, // Filtering existing items which are in the same state as the items that are being updated, to check for duplicates.
                    itemState = listItemState
            ).flatMap { updatedItems ->
                // Filtering out the items updated in the deduplication process
                val itemsToDelete = arrayListOf<ListItemEntity>()
                val filteredItemsToUpdate = arrayListOf<ListItemEntity>()
                itemsToUpdate.map { item ->
                    if (updatedItems.parallelStream().anyMatch { it.itemRefId == item.itemRefId }) {
                        itemsToDelete.add(item) // The item was deduped so delete the existing item in previous state.
                    } else {
                        filteredItemsToUpdate.add(item)
                    }
                }
                Flux.fromIterable(filteredItemsToUpdate.asIterable()).flatMap {
                    updateListItemManager.updateListItem(guestId, listId, it.copy(itemState = listItemState.value), it)
                }.collectList().zipWith(
                        deleteListItemsManager.deleteListItems(guestId, listId, itemsToDelete)
                ).map { itemsToUpdate }
            }
        }
    }
}
