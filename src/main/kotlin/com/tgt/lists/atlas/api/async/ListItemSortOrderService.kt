package com.tgt.lists.atlas.api.async

import com.tgt.lists.atlas.api.domain.ListItemSortOrderManager
import com.tgt.lists.atlas.api.transport.EditItemSortOrderRequestTO
import com.tgt.lists.atlas.api.transport.ListItemMetaDataTO
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
        listId: UUID,
        itemId: UUID,
        listItemMetaDataTO: ListItemMetaDataTO
    ): Mono<Boolean> {

        logger.debug("[saveListItemSortOrder] listId: $listId, itemId: $itemId")

        return if (listItemMetaDataTO.itemState == LIST_ITEM_STATE.PENDING) {
            listItemSortOrderManager.saveNewListItemOrder(listId, itemId).map { true }
                    .onErrorResume {
                        logger.error("Exception while saving list item sort order", it)
                        Mono.just(false)
                    }
        } else {
            Mono.just(true)
        }
    }

    fun deleteListItemSortOrder(
        listId: UUID,
        deleteListItems: List<MultiDeleteListItem>
    ): Mono<Boolean> {

        logger.debug("[deleteListItemSortOrder] listId: $listId, deleteListItems: $deleteListItems")

        return deleteListItems.filter { it.listItemMetaDataTO.itemState == LIST_ITEM_STATE.PENDING }
                .takeIf { !it.isNullOrEmpty() }
                ?.let { listItemSortOrderManager.removeListItemIdFromSortOrder(listId, it.map { it.itemId }.toTypedArray())
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

        return listItemSortOrderManager.updateListItemSortOrder(editItemSortOrderRequestTO.listId,
                editItemSortOrderRequestTO.primaryItemId, editItemSortOrderRequestTO.secondaryItemId,
                editItemSortOrderRequestTO.direction).map { true }
                .onErrorResume {
                    logger.error("Exception while editing list sort order", it)
                    Mono.just(false)
                }
    }
}
