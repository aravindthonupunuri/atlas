package com.tgt.lists.atlas.api.service

import com.tgt.lists.cart.transport.CartItemResponse
import com.tgt.lists.atlas.api.domain.CartManager
import com.tgt.lists.atlas.api.domain.EventPublisher
import com.tgt.lists.atlas.api.transport.ListItemMultiStateUpdateResponseTO
import com.tgt.lists.atlas.api.util.CartManagerName
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.api.util.getListItemMetaDataFromCart
import com.tgt.lists.atlas.kafka.model.CompletionItemActionEvent
import com.tgt.lists.atlas.kafka.model.PendingItemActionEvent
import mu.KotlinLogging
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateMultipleListItemStateService(
    @CartManagerName("UpdateMultipleListItemStateService") @Inject private val cartManager: CartManager,
    @Inject private val eventPublisher: EventPublisher
) {

    private val logger = KotlinLogging.logger {}

    /**
     *
     * The method implements the functionality to update multiple items state.
     *
     */
    fun updateMultipleListItemState(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        listId: UUID,
        listItemState: LIST_ITEM_STATE,
        locationId: Long,
        itemIds: List<UUID>
    ): Mono<ListItemMultiStateUpdateResponseTO> {

        logger.debug("[updateMultipleListItemState] guestId: $guestId, listId: $listId, listItemState: $listItemState, itemIds: $itemIds, locationId: $locationId")

        return validateItems(listId, itemIds, listItemState)
                .flatMap { publishMultiItemUpdateEvents(it, guestId, locationId, listId, listItemState) }
                .map {
                    val successItems = arrayListOf<UUID>()
                    successItems.addAll(it.first)
                    successItems.addAll(it.second)
                    ListItemMultiStateUpdateResponseTO(listId, successListItemIds = successItems,
                            failedListItemIds = it.third)
                }
    }

    /**
     *
     * The method implements the functionality to validate the itemsIds to be updated.
     * validItems: Items that can be updated into the specified item state
     * updatedItems: Items that are already in the state specified in the request, so we do not process these ids
     * invalidItems: Items that are not p[art of the list
     *
     */
    private fun validateItems(
        listId: UUID,
        itemIds: List<UUID>,
        listItemState: LIST_ITEM_STATE
    ): Mono<Triple<List<UUID>, List<UUID>, List<UUID>>> {
        val validItems = arrayListOf<UUID>()
        val updatedItems = arrayListOf<UUID>()
        val invalidItems = arrayListOf<UUID>()

        return getCartItems(listId).map {
            val existingCartItems = it
            itemIds.stream().forEach { itemId ->
                val cartItem = existingCartItems.filter { it.cartItemId == itemId }.firstOrNull()
                if (cartItem != null) {
                    val itemState = getListItemMetaDataFromCart(cartItem.metadata)?.itemState
                    if (itemState == listItemState) { updatedItems.add(itemId) } else { validItems.add(itemId) }
                    } else {
                    invalidItems.add(itemId)
                }
            }
            logger.debug("ValidItems: $validItems, UpdatedItems: $updatedItems, InvalidItems: $invalidItems ")

            Triple(validItems, updatedItems, invalidItems)
        }
    }

    /**
     *
     * The method implements the functionality to get all the items in a list.
     * It includes items in both pending list and completed list.
     *
     */
    private fun getCartItems(
        listId: UUID
    ): Mono<List<CartItemResponse>> {
        return cartManager.getCompletedListCart(listId)
                .switchIfEmpty {
                    throw RuntimeException("Completed cart not found")
                }
                .flatMap { getCartContents(cartId = it.cartId!!) }
                .zipWith(getCartContents(cartId = listId))
                .map {
                    val totalCartItems = arrayListOf<CartItemResponse>()
                    totalCartItems.addAll(it.t1)
                    totalCartItems.addAll(it.t2)
                    totalCartItems
                }
    }

    private fun getCartContents(
        cartId: UUID
    ): Mono<List<CartItemResponse>> {
        return cartManager.getListCartContents(cartId, true)
                .map { it.cartItems?.toList() ?: emptyList() }
                .switchIfEmpty {
                    logger.debug("From getCartContents() in UpdateMultipleListItemStateService," +
                            " Empty cart Contents, listId: $cartId")
                    Mono.just(emptyList())
                }
    }

    /**
     *
     * The method implements the functionality to publish CompletionItemActionEvent or PendingItemActionEvent
     * for processing item state change.
     *
     */
    private fun publishMultiItemUpdateEvents(
        it: Triple<List<UUID>, List<UUID>, List<UUID>>,
        guestId: String,
        locationId: Long,
        listId: UUID,
        listItemState: LIST_ITEM_STATE
    ): Mono<Triple<List<UUID>, List<UUID>, List<UUID>>> {
        if (it.first.isNullOrEmpty()) {
            logger.debug("From publishMultiItemUpdateEvents() in UpdateMultipleListItemStateService," +
                    " No valid items to update, triple: $it")
            return Mono.just(it)
        }

        return when (listItemState) {
            LIST_ITEM_STATE.PENDING -> eventPublisher.publishEvent(PendingItemActionEvent.getEventType(),
                    PendingItemActionEvent(guestId, locationId, listId, it.first), listId.toString())
                    .then(Mono.just(it))
            LIST_ITEM_STATE.COMPLETED -> eventPublisher.publishEvent(CompletionItemActionEvent.getEventType(),
                    CompletionItemActionEvent(guestId, locationId, listId, it.first), listId.toString())
                    .then(Mono.just(it))
        }
    }
}
