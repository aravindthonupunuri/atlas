package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.domain.ListItemSortOrderManager
import com.tgt.lists.atlas.api.transport.EditItemSortOrderRequestTO
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.kafka.model.MultiDeleteListItem
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListItemSortOrderService(
    @Inject private val listItemSortOrderManager: ListItemSortOrderManager
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
            listItemSortOrderManager.saveNewListItemOrder(guestId, listId, itemId)
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
        deleteListItems: List<MultiDeleteListItem>
    ): Mono<Boolean> {

        logger.debug("[deleteListItemSortOrder] guestId: $guestId listId: $listId, deleteListItems: $deleteListItems")

        return deleteListItems.filter { it.itemState == LIST_ITEM_STATE.PENDING }
                .takeIf { !it.isNullOrEmpty() }
                ?.let { listItemSortOrderManager.removeListItemIdFromSortOrder(guestId, listId, it.map { it.itemId }.toTypedArray())
                            .map { true }
                            .onErrorResume {
                                logger.error("Exception while deleting list item sort order", it)
                                Mono.just(false)
                            }
                } ?: Mono.just(true)
    }

    fun editListItemSortOrder(
        editItemSortOrderRequestTO: EditItemSortOrderRequestTO
    ): Mono<Boolean> {

        logger.debug("[editListItemSortOrder] editItemSortOrderRequestTO: $editItemSortOrderRequestTO")

        return listItemSortOrderManager.updateListItemSortOrder(editItemSortOrderRequestTO.guestId,
                editItemSortOrderRequestTO.listId, editItemSortOrderRequestTO.primaryItemId,
                editItemSortOrderRequestTO.secondaryItemId, editItemSortOrderRequestTO.direction)
                .map { true }
                .onErrorResume {
                    logger.error("Exception while editing list sort order", it)
                    Mono.just(false)
                }
    }
}
