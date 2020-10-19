package com.tgt.lists.atlas.api.async

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tgt.lists.cart.transport.CartItemResponse
import com.tgt.lists.common.components.exception.ResourceNotFoundException
import com.tgt.lists.atlas.api.domain.AddMultiItemsManager
import com.tgt.lists.atlas.api.domain.CartManager
import com.tgt.lists.atlas.api.domain.DeleteCartItemsManager
import com.tgt.lists.atlas.api.transport.toListItemRequestTO
import com.tgt.lists.atlas.api.util.AppErrorCodes
import com.tgt.lists.atlas.api.util.CartManagerName
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import mu.KotlinLogging
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingToCompletedItemStateService(
    @CartManagerName("PendingToCompletedItemStateService") @Inject private val cartManager: CartManager,
    @Inject private val deleteCartItemsManager: DeleteCartItemsManager,
    @Inject private val addMultiItemsManager: AddMultiItemsManager
) {

    private val logger = KotlinLogging.logger { PendingToCompletedItemStateService::class.java.name }

    /**
     *
     * Implements the functionality to update items state from pending to completed.
     * This is done by creating items in completed list and deleting the items in pending list
     *
     */
    fun processPendingToCompletedItemState(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        locationId: Long,
        listId: UUID,
        itemIds: List<UUID>,
        retryState: RetryState
    ): Mono<RetryState> {
        return when {
            retryState.incompleteState() -> {
                logger.debug("From processPendingToCompletedItemState(), starting processing for guestId: $guestId, listId: $listId, itemIds: $itemIds")
                return getCompletedCartId(listId).zipWith(searchItemsInPendingList(listId, itemIds))
                        .flatMap { processItemStateChange(guestId, locationId, listId, it.t1, it.t2, retryState) }
                        .onErrorResume { Mono.just(retryState) }
                        .switchIfEmpty { Mono.just(retryState) }
            }
            retryState.partialCompleteState() -> {
                logger.debug("From processPendingToCompletedItemState(), starting processing of partially completed state for guestId: $guestId, listId: $listId, itemIds: $itemIds")
                return searchItemsInPendingList(listId, itemIds)
                        .flatMap { deleteItemsInPendingList(guestId, listId, it, retryState) }
                        .onErrorResume { Mono.just(retryState) }
                        .switchIfEmpty { Mono.just(retryState) }
            }
            retryState.completeState() -> {
                logger.debug("From processPendingToCompletedItemState(), processing complete")
                Mono.just(retryState)
            }
            else -> {
                logger.error("Unknown step for guestId $guestId with listId $listId from processPendingToCompletedItemState()")
                retryState.createItemsInCompletedList = true
                retryState.deleteItemsInPendingList = true
                return Mono.just(retryState)
            }
        }
    }

    private fun getCompletedCartId(
        listId: UUID
    ): Mono<UUID> {
        return cartManager.getCompletedListCart(listId)
                .switchIfEmpty {
                    throw ResourceNotFoundException(AppErrorCodes.RESOURCE_NOT_FOUND_ERROR_CODE(listOf("List $listId not found")))
                }.map { it.cartId }
    }

    /**
     *
     * Implements the functionality to get items to be updated to completed from the pending state
     *
     */
    private fun searchItemsInPendingList(
        listId: UUID,
        itemIds: List<UUID>
    ): Mono<List<CartItemResponse>> {
        return cartManager.getListCartContents(listId, true)
                .switchIfEmpty {
                    throw ResourceNotFoundException(AppErrorCodes.RESOURCE_NOT_FOUND_ERROR_CODE(listOf("List: $listId Item: $itemIds not found")))
                }
                .map {
                    val cartItems = arrayListOf<CartItemResponse>()
                    val existingCartItems = it.cartItems!!.toList()
                    itemIds.stream().forEach { itemId ->
                        existingCartItems.stream().forEach { cartItemResponse ->
                            if (cartItemResponse.cartItemId == itemId) {
                                cartItems.add(cartItemResponse)
                            }
                        }
                    }
                    cartItems
                }
    }

    /**
     *
     * Implements the functionality to create the items in the completed list and delete the items in pending list
     *
     */
    private fun processItemStateChange(
        guestId: String,
        locationId: Long,
        listId: UUID,
        completedCartId: UUID,
        items: List<CartItemResponse>,
        retryState: RetryState
    ): Mono<RetryState> {
        return if (items.isNullOrEmpty()) {
            logger.error("From processEvent(), Items to update not found in pending list")
            Mono.just(retryState)
        } else {
            createItemsInCompletedList(guestId, locationId, listId, completedCartId, items, retryState)
                    .flatMap { deleteItemsInPendingList(guestId, listId, items, it) }
        }
    }

    /**
     *
     * Implements the functionality to create the multiple items in the completed list
     *
     */
    private fun createItemsInCompletedList(
        guestId: String,
        locationId: Long,
        listId: UUID,
        completedCartId: UUID,
        items: List<CartItemResponse>,
        retryState: RetryState
    ): Mono<RetryState> {
        val newItems = items.map { toListItemRequestTO(it) }
        logger.debug("From createItemsInCompletedList(), creating items in completed list")
        return addMultiItemsManager.processAddMultiItems(guestId, locationId, listId, completedCartId,
                LIST_ITEM_STATE.COMPLETED, newItems)
                .map {
                    retryState.createItemsInCompletedList = true
                    retryState
                }
                .onErrorResume {
                    logger.error("Exception from createItemsInCompletedList() with [error: ${it.message}]", it)
                    Mono.just(retryState)
                }
    }

    /**
     *
     * Implements the functionality to delete the multiple items in the pending list
     *
     */
    private fun deleteItemsInPendingList(
        guestId: String,
        listId: UUID,
        items: List<CartItemResponse>,
        retryState: RetryState
    ): Mono<RetryState> {
        if (!retryState.createItemsInCompletedList) {
            logger.debug("From deleteItemsInPendingList(),Exception creating items in completed list")
            return Mono.just(retryState)
        }
        logger.debug("From deleteItemsInPendingList(), deleting items in pending list")
        return deleteCartItemsManager.deleteMultipleCartItems(guestId, listId, listId, items.toTypedArray())
                .map {
                    retryState.deleteItemsInPendingList = true
                    retryState
                }
                .switchIfEmpty {
                    logger.error("From deleteItemInCompletedList(), Item already deleted from completed list")
                    retryState.deleteItemsInPendingList = true
                    Mono.just(retryState)
                }
                .onErrorResume {
                    logger.error("Exception deleting cart items in pending list from " +
                            "deleteItemsInPendingList() with [error: ${it.message}]", it)
                    Mono.just(retryState)
                }
    }

    data class RetryState(
        var createItemsInCompletedList: Boolean = false,
        var deleteItemsInPendingList: Boolean = false
    ) {
        fun completeState(): Boolean {
            return createItemsInCompletedList && deleteItemsInPendingList
        }

        fun incompleteState(): Boolean {
            return !createItemsInCompletedList && !deleteItemsInPendingList
        }

        fun partialCompleteState(): Boolean {
            return createItemsInCompletedList && !deleteItemsInPendingList
        }

        companion object {
            // jacksonObjectMapper() returns a normal ObjectMapper with the KotlinModule registered
            val jsonMapper: ObjectMapper = jacksonObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)

            @JvmStatic
            fun deserialize(retryState: String): RetryState {
                return jsonMapper.readValue<RetryState>(retryState, RetryState::class.java)
            }

            @JvmStatic
            fun serialize(retryState: RetryState): String {
                return jsonMapper.writeValueAsString(retryState)
            }
        }
    }
}
