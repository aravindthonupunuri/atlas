package com.tgt.lists.atlas.api.domain

import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.transport.ListItemRequestTO
import com.tgt.lists.atlas.api.transport.mapper.ListItemMapper
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddListItemsManager(
    @Inject private val deduplicationManager: DeduplicationManager,
    @Inject private val insertListItemsManager: InsertListItemsManager
) {

    private val logger = KotlinLogging.logger { AddListItemsManager::class.java.name }

    /**
     *
     * Implements the functionality to add multiple cart items. Items already existing in the list
     * are updated and new items are added to the list
     *
     */
    fun addListItems(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        listId: UUID,
        listItemState: LIST_ITEM_STATE,
        newItems: List<ListItemRequestTO>
    ): Mono<List<ListItemEntity>> {
        val newItemsMap = newItems.map {
            it.itemRefId to it
        }.toMap()

        return deduplicationManager.updateDuplicateItems(guestId, listId, newItemsMap, listItemState)
                .flatMap { addNewItems(guestId, listId, newItemsMap, it.first, it.second) }
    }

    /**
     *
     * Implements the functionality to add multiple cart items after completing the deduplication process.
     *
     */
    private fun addNewItems(
        guestId: String,
        listId: UUID,
        newItemsMap: Map<String, ListItemRequestTO>,
        updatedItems: List<ListItemEntity>,
        duplicateItemsMap: MutableMap<String, List<ListItemEntity>>
    ): Mono<List<ListItemEntity>> {
        val itemsToCreate = newItemsMap.filter { !duplicateItemsMap.contains(it.key) }
                .map { ListItemMapper.toNewListItemEntity(listId, it.value) }
        val listItems = arrayListOf<ListItemEntity>()

        if (itemsToCreate.isNullOrEmpty()) {
            logger.debug("From addNewItems(), No items to add")
            listItems.addAll(updatedItems)
            return Mono.just(listItems)
        }

        return insertListItemsManager.insertListItems(guestId, listId, itemsToCreate, false)
                .map {
                    listItems.addAll(it)
                    listItems.addAll(updatedItems)
                    listItems
                }
    }
}
