package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.domain.ListPreferenceSortOrderManager
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.transport.EditItemSortOrderRequestTO
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListItemSortOrderService(
    @Inject private val listPreferenceSortOrderManager: ListPreferenceSortOrderManager
) {

    private val logger = KotlinLogging.logger { ListItemSortOrderService::class.java.name }

    fun saveListItemSortOrder(
        guestId: String,
        listId: UUID,
        itemId: UUID,
        listItemState: LIST_ITEM_STATE
    ): Mono<Boolean> {

        logger.debug("[saveListItemSortOrder] guestId: $guestId listId: $listId, itemId: $itemId")

        return if (listItemState == LIST_ITEM_STATE.PENDING) {
            listPreferenceSortOrderManager.saveNewListItemOrder(guestId, listId, itemId)
                    .map { true }
                    .onErrorResume {
                        logger.error("Exception while saving list item sort order", it)
                        Mono.just(false)
                    }
        } else { Mono.just(true) }
    }

    fun deleteListItemSortOrder(
        guestId: String,
        listId: UUID,
        deleteListItems: List<ListItemEntity>
    ): Mono<Boolean> {

        logger.debug("[deleteListItemSortOrder] guestId: $guestId listId: $listId, deleteListItems: $deleteListItems")

        val itemIdsToDelete = deleteListItems.map { item -> item.itemId!! }.toTypedArray()
        return listPreferenceSortOrderManager.removeListItemIdFromSortOrder(guestId, listId, itemIdsToDelete)
                .map { true }
                .onErrorResume {
                    logger.error("Exception while deleting list item sort order", it)
                    Mono.just(false)
                }
    }

    fun editListItemSortOrder(
        editItemSortOrderRequestTO: EditItemSortOrderRequestTO
    ): Mono<Boolean> {

        logger.debug("[editListItemSortOrder] editItemSortOrderRequestTO: $editItemSortOrderRequestTO")

        return listPreferenceSortOrderManager.updateListItemSortOrder(editItemSortOrderRequestTO.guestId,
                editItemSortOrderRequestTO.listId, editItemSortOrderRequestTO.primaryItemId,
                editItemSortOrderRequestTO.secondaryItemId, editItemSortOrderRequestTO.direction)
                .map { true }
                .onErrorResume {
                    logger.error("Exception while editing list item sort order", it)
                    Mono.just(false)
                }
    }
}
