package com.tgt.lists.atlas.api.service

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
    @Inject private val updateListItemManager: UpdateListItemManager
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
        itemIds: List<UUID>
    ): Mono<ListItemsStateUpdateResponseTO> {

        logger.debug("[updateMultipleListItemState] guestId: $guestId, listId: $listId, listItemState: $listItemState, itemIds: $itemIds, locationId: $locationId")

        return validateItems(listId, itemIds, listItemState).flatMap { triple ->
            updateState(guestId, listId, triple.first, listItemState).map { updatedItems ->
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
        listId: UUID,
        listItemIds: List<UUID>,
        listItemState: LIST_ITEM_STATE
    ): Mono<Triple<List<ListItemEntity>, List<UUID>, List<UUID>>> {
        val itemsToUpdate = arrayListOf<ListItemEntity>()
        val itemsAlreadyUpdated = arrayListOf<UUID>()
        val invalidItems = arrayListOf<UUID>()

        return listRepository.findListItemsByListId(listId).collectList().map { items ->
            listItemIds.stream().forEach { itemId ->
                val item = items.firstOrNull { it.itemId == itemId }
                if (item != null) {
                    val itemState = item.itemState!!
                    if (itemState == listItemState.name) {
                        itemsAlreadyUpdated.add(itemId)
                    } else {
                        itemsToUpdate.add(item)
                    }
                } else {
                    invalidItems.add(itemId)
                }
            }
            logger.debug("ItemsToUpdate: ${itemsToUpdate.map { it.itemId } }, ItemsAlreadyUpdated: $itemsAlreadyUpdated, InvalidItems: $invalidItems ")

            Triple(itemsToUpdate, itemsAlreadyUpdated, invalidItems)
        }
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
        itemsToUpdate: List<ListItemEntity>,
        listItemState: LIST_ITEM_STATE
    ): Mono<List<ListItemEntity>> {
        return if (itemsToUpdate.isNullOrEmpty()) {
            logger.debug("[updateState] No items to update")
            Mono.just(itemsToUpdate)
        } else {
            Flux.fromIterable(itemsToUpdate.asIterable()).flatMap {
                updateListItemManager.updateListItem(guestId, listId, it.copy(itemState = listItemState.name), it)
            }.collectList()
        }
    }
}
