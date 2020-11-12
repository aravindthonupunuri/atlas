package com.tgt.lists.atlas.api.domain

import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
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
        items: List<ListItemEntity>,
        existingItems: List<ListItemEntity>,
        itemState: LIST_ITEM_STATE
    ): Mono<List<ListItemEntity>> {
        return if (existingItems.isNullOrEmpty()) {
            logger.debug("[updateDuplicateItems] No existing items in list skipping deduplication process")
                Mono.just(emptyList())
            } else {
            val duplicateItemsMap = getDuplicateItemsMap(existingItems, items)
            return checkMaxItemsCount(guestId, listId, existingItems, items, itemState, duplicateItemsMap)
                    .flatMap { processDedupe(guestId, listId, items, duplicateItemsMap) }
        }
    }

    /**
     * Implements functionality to find the duplicates for every new item being added.
     */
    private fun getDuplicateItemsMap(
        existingItems: List<ListItemEntity>,
        items: List<ListItemEntity>
    ): MutableMap<String, List<ListItemEntity>> {
        if (!isDedupeEnabled) {
            logger.debug("[getDuplicateItemsMap] Deduplication is turned off")
            return mutableMapOf()
        } else {
            val duplicateItemsMap = mutableMapOf<String, List<ListItemEntity>>()
            items.stream().forEach { listItemEntity ->
                val duplicateItems = existingItems.filter { it.itemRefId == listItemEntity.itemRefId }
                if (!duplicateItems.isNullOrEmpty()) {
                    duplicateItemsMap[listItemEntity.itemRefId!!] = duplicateItems
                }
            }

            logger.debug("DuplicateItemsMap: keys ${duplicateItemsMap.keys} and " +
                    "values: ${duplicateItemsMap.values.forEach { it.forEach { listItem -> listItem.itemId } }}")

            return duplicateItemsMap
        }
    }

    /**
     * Implements functionality to update preexisting items and also delete items not deduped
     * already existing in the list.
     *
     * Give a pair of (list of updatedItems, duplicateItemsMap)
     */
    private fun processDedupe(
        guestId: String,
        listId: UUID,
        items: List<ListItemEntity>,
        duplicateItemsMap: MutableMap<String, List<ListItemEntity>>
    ): Mono<List<ListItemEntity>> {
        val deDupeData = arrayListOf<DedupeData>()
        val itemsMap = items.map { it.itemRefId to it }.toMap()

        duplicateItemsMap.map { it ->
            val newItem = itemsMap[it.key]
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

                updatedRequestedQuantity += (newItem.itemReqQty ?: 1)
                updatedItemNote = if (newItem.itemNotes.isNullOrEmpty()) {
                    updatedItemNote
                } else {
                    newItem.itemNotes + "\n" + updatedItemNote
                }

                val itemToUpdate = sortedExistingItemList.first()
                        .copy(itemNotes = updatedItemNote, itemReqQty = updatedRequestedQuantity)
                val itemsToDelete = sortedExistingItemList
                        .filter { it.itemId != sortedExistingItemList.first().itemId }

                deDupeData.add(DedupeData(itemToUpdate, itemsToDelete))
            }
        }

        val totalItemsToDelete = arrayListOf<ListItemEntity>()
        deDupeData.map { totalItemsToDelete.addAll(it.itemsToDelete) }

        val totalItemsToUpdate = deDupeData.map { it.itemToUpdate }

        return if (totalItemsToUpdate.isNullOrEmpty()) {
            logger.debug("[processDedupe] No items to dedupe")
            Mono.just(emptyList())
        } else {
            val itemIdsToDelete = totalItemsToDelete.map { it.itemId!! }
            logger.debug("[processDedupe] Items to Update: $totalItemsToUpdate ItemIds to delete: $itemIdsToDelete")

            Flux.fromIterable(totalItemsToUpdate.asIterable())
                    .flatMap { updateListItemManager.updateListItem(guestId, listId, it) }.collectList()
                    .zipWith(deleteListItemsManager.deleteListItems(guestId, listId, totalItemsToDelete))
                    .map { it.t1 }
        }
    }

    private fun checkMaxItemsCount(
        guestId: String,
        listId: UUID,
        existingItems: List<ListItemEntity>,
        itemsToDedup: List<ListItemEntity>,
        itemState: LIST_ITEM_STATE,
        duplicateItemsMap: MutableMap<String, List<ListItemEntity>> // map of new item ref id to its duplicate existing items
    ): Mono<Boolean> {
        // Validate max items count
        val existingItemsCount = existingItems.count() // count before dedup
        var count = 0
        duplicateItemsMap.keys.stream().forEach { count += (duplicateItemsMap[it]?.count() ?: 0) }
        val duplicateItemsCount = count // total no of duplicated items existing in list
        val newItemsCount = itemsToDedup.count() // new items to be added

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

            return deleteListItemsManager.deleteListItems(guestId, listId, result.take(itemsCountToDelete)).map { true }
        } else {
            return Mono.just(true)
        }
    }

    data class DedupeData(
        val itemToUpdate: ListItemEntity,
        val itemsToDelete: List<ListItemEntity>
    )
}
