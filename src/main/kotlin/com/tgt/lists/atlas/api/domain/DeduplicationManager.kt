package com.tgt.lists.atlas.api.domain

import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.ListItemRequestTO
import com.tgt.lists.atlas.api.util.AppErrorCodes
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.common.components.exception.BadRequestException
import io.micronaut.context.annotation.Value
import mu.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*
import java.util.stream.Collectors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeduplicationManager(
    @Inject private val listRepository: ListRepository,
    @Inject private val updateListItemManager: UpdateListItemManager,
    @Inject private val deleteListItemsManager: DeleteListItemsManager,
    @Value("\${list.features.dedupe}") private val isDedupeEnabled: Boolean,
    @Value("\${list.max-pending-item-count}")
    private var maxPendingItemCount: Int = 100, // default max pending item count
    @Value("\${list.max-completed-items-count}")
    private var maxCompletedItemsCount: Int = 100, // default max completed item count
    @Value("\${list.pending-list-rolling-update}")
    private var rollingUpdate: Boolean = false // default max completed item count
) {
    private val logger = KotlinLogging.logger { DeduplicationManager::class.java.name }

    /**
     * Implements logic to find all the duplicate items found in the given bulk item list.
     * The response of this method is a Triple of existing items, updated items and duplicateItemsMap
     *
     * Give a pair of (list of updatedItems, duplicateItemsMap)
     */
    fun updateDuplicateItems(
        guestId: String,
        listId: UUID,
        newItemsMap: Map<String, ListItemRequestTO>,
        itemState: LIST_ITEM_STATE
    ): Mono<Pair<List<ListItemEntity>, MutableMap<String, List<ListItemEntity>>>> {
        return listRepository.findListItemsByListId(listId).collectList().flatMap {
            if (it.isNullOrEmpty()) {
                logger.error("From updateDuplicateItems(), empty list with no list items")
                Mono.just(Pair(emptyList(), mutableMapOf()))
            } else {
                val existingItems: List<ListItemEntity> = if (itemState == LIST_ITEM_STATE.PENDING) {
                    it.filter { listItem -> listItem.itemState == LIST_ITEM_STATE.PENDING.value }
                } else {
                    it.filter { listItem -> listItem.itemState == LIST_ITEM_STATE.COMPLETED.value }
                }
                processDeduplication(guestId, listId, itemState, existingItems, newItemsMap)
            }
        }
    }

    /**
     * Implements functionality to verify the final items count does not exceed the limit.
     * It also updates the duplicate items already present in the list.
     *
     *  Give a triple of (list of existing items, list of updatedItems, duplicateItemsMap)
     */
    private fun processDeduplication(
        guestId: String,
        listId: UUID,
        itemState: LIST_ITEM_STATE,
        existingItems: List<ListItemEntity>,
        newItemsMap: Map<String, ListItemRequestTO>
    ): Mono<Pair<List<ListItemEntity>, MutableMap<String, List<ListItemEntity>>>> {
        if (existingItems.isNullOrEmpty()) {
            logger.debug("[processDeduplication] No existing items in list skipping deduplication process")
            return Mono.just(Pair(emptyList(), mutableMapOf()))
        }

        val newItems = newItemsMap.values.toList() // new items to be added
        val duplicateItemsMap = getDuplicateItemsMap(existingItems, newItems)

        return checkMaxItemsCount(guestId, listId, existingItems, newItems, itemState, duplicateItemsMap)
                .flatMap { updateDuplicateItems(guestId, listId, newItemsMap, it) }
                .map { Pair(it.first, it.second) }
    }

    /**
     * Implements functionality to update preexisting items and also delete items not deduped
     * already existing in the list.
     *
     * Give a pair of (list of updatedItems, duplicateItemsMap)
     */
    private fun updateDuplicateItems(
        guestId: String,
        listId: UUID,
        newItemsMap: Map<String, ListItemRequestTO>,
        duplicateItemsMap: MutableMap<String, List<ListItemEntity>>
    ): Mono<Pair<List<ListItemEntity>, MutableMap<String, List<ListItemEntity>>>> {
        val dedupeDataList = arrayListOf<DedupeData>()

        duplicateItemsMap.map { it ->
            val newItem = newItemsMap[it.key]
            val duplicateItems = it.value

            if (newItem != null && !duplicateItems.isNullOrEmpty()) {
                val sortedExistingItemList = duplicateItems
                        .sortedBy { listItem -> listItem.itemCreatedAt }
                var updatedRequestedQuantity = 0
                var updatedItemNote = ""

                for (listItem in sortedExistingItemList) {
                    if (!listItem.itemNotes.isNullOrEmpty()) {
                        updatedItemNote = if (updatedItemNote.isNotEmpty()) {
                            listItem.itemNotes.toString() + "\n" + updatedItemNote
                        } else {
                            listItem.itemNotes.toString()
                        }
                    }
                    updatedRequestedQuantity += listItem.itemReqQty ?: 1
                }

                updatedRequestedQuantity += (newItem.requestedQuantity ?: 1)
                updatedItemNote = if (newItem.itemNote.isNullOrEmpty()) {
                    updatedItemNote
                } else {
                    newItem.itemNote + "\n" + updatedItemNote
                }

                val itemToUpdate = sortedExistingItemList.first()
                        .copy(itemNotes = updatedItemNote, itemReqQty = updatedRequestedQuantity)
                val itemsToDelete = sortedExistingItemList
                        .filter { it.itemId != sortedExistingItemList.first().itemId }

                dedupeDataList.add(DedupeData(itemToUpdate, itemsToDelete))
            }
        }

        val totalItemsToDelete = arrayListOf<ListItemEntity>()
        dedupeDataList.map { totalItemsToDelete.addAll(it.itemsToDelete) }

        val totalItemsToUpdate = dedupeDataList.map { it.itemToUpdate }

        if (totalItemsToUpdate.isNullOrEmpty()) {
            logger.debug("[updateDuplicateItems] No items to dedupe")
            return Mono.just(Pair(emptyList(), duplicateItemsMap))
        }

        val itemIdsToDelete = totalItemsToDelete.map { it.itemId!! }
        logger.debug("[updateDuplicateItems] Items to Update: $totalItemsToUpdate ItemIds to delete: $itemIdsToDelete")

        return Flux.fromIterable(totalItemsToUpdate.asIterable())
                .flatMap { updateListItemManager.updateListItem(guestId, listId, it) }.collectList()
                .zipWith(deleteListItemsManager.deleteListItems(guestId, listId, totalItemsToDelete))
                .map { Pair(it.t1, duplicateItemsMap) }
    }

    private fun checkMaxItemsCount(
        guestId: String,
        listId: UUID,
        existingItems: List<ListItemEntity>,
        newItems: List<ListItemRequestTO>,
        itemState: LIST_ITEM_STATE,
        duplicateItemsMap: MutableMap<String, List<ListItemEntity>> // map of new item ref id to its duplicate existing items
    ): Mono<MutableMap<String, List<ListItemEntity>>> {
        // Validate max items count
        val existingItemsCount = existingItems.count() // count before dedup
        var count = 0
        duplicateItemsMap.keys.stream().forEach { count += (duplicateItemsMap[it]?.count() ?: 0) }
        val duplicateItemsCount = count // total no of duplicated items existing in list
        val newItemsCount = newItems.count() // new items to be added

        val finalItemsCount = (existingItemsCount - duplicateItemsCount) + newItemsCount // final items count after dedupe
        if (itemState == LIST_ITEM_STATE.PENDING && !rollingUpdate && finalItemsCount > maxPendingItemCount) {
            throw BadRequestException(AppErrorCodes.BAD_REQUEST_ERROR_CODE(listOf("Exceeding max items count")))
        } else if ((itemState == LIST_ITEM_STATE.PENDING && rollingUpdate && finalItemsCount > maxPendingItemCount) ||
                (itemState == LIST_ITEM_STATE.COMPLETED && finalItemsCount > maxCompletedItemsCount)) {
            logger.error("Exceeding max items count in list, so deleting stale items")
            val maxItemCount = if (itemState == LIST_ITEM_STATE.PENDING) {
                maxPendingItemCount
            } else {
                maxCompletedItemsCount
            }
            val itemsCountToDelete = finalItemsCount - maxItemCount
            val duplicateItems = arrayListOf<ListItemEntity>()
            duplicateItemsMap.values.forEach { duplicateItems.addAll(it) }
            // The result will consist of items to be deleted which are old.
            // existingItems -> Items that are present in the list before dedup process.
            // Filter the duplicate items in existingItems since these items will be deleted during the deduplication process in updateMultiItems() method.
            // So the final items to be deleted since the list has reached max items count would be (existingItems - duplicate items).
            val result = existingItems.stream()
                    .filter { listItem -> duplicateItems.stream()
                            .noneMatch { it.itemId == listItem.itemId } }.collect(Collectors.toList())
            result.sortBy { it.itemUpdatedAt }
            logger.debug("[checkMaxItemsCount] Exceeding max items count in list, stale items to be delete count" +
                    " $itemsCountToDelete")

            return deleteListItemsManager.deleteListItems(guestId, listId, result.take(itemsCountToDelete))
                    .map { duplicateItemsMap }
                    .onErrorResume {
                        logger.error { it.message ?: it.cause?.message }
                        Mono.just(duplicateItemsMap)
                    }
        } else {
            return Mono.just(duplicateItemsMap)
        }
    }

    /**
     * Implements functionality to find the duplicates for every new item being added.
     */
    private fun getDuplicateItemsMap(
        existingItems: List<ListItemEntity>,
        newItems: List<ListItemRequestTO>
    ): MutableMap<String, List<ListItemEntity>> {
        if (!isDedupeEnabled) {
            logger.debug("[getDuplicateItemsMap] Deduplication is turned off")
            return mutableMapOf()
        } else {
            val duplicateItemsMap = mutableMapOf<String, List<ListItemEntity>>()
            newItems.stream().forEach { listItemRequestTO ->
                val duplicateItems = existingItems.filter { itemFilter(it, listItemRequestTO) }
                if (!duplicateItems.isNullOrEmpty()) {
                    duplicateItemsMap[listItemRequestTO.itemRefId] = duplicateItems
                }
            }

            logger.debug("DuplicateItemsMap: keys ${duplicateItemsMap.keys} and " +
                    "values: ${duplicateItemsMap.values.forEach { it.forEach { listItem -> listItem.itemId } }}")

            return duplicateItemsMap
        }
    }

    private fun itemFilter(existingItem: ListItemEntity, newItem: ListItemRequestTO): Boolean {
        return (existingItem.itemType == newItem.itemType.value) &&
                (newItem.itemRefId == existingItem.itemRefId)
    }

    data class DedupeData(
        val itemToUpdate: ListItemEntity,
        val itemsToDelete: List<ListItemEntity>
    )
}
