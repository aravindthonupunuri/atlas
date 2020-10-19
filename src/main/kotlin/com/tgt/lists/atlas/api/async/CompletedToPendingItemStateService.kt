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
class CompletedToPendingItemStateService(
    @CartManagerName("CompletedToPendingItemStateService") @Inject private val cartManager: CartManager,
    @Inject private val deleteCartItemsManager: DeleteCartItemsManager,
    @Inject private val addMultiItemsManager: AddMultiItemsManager
) {

    private val logger = KotlinLogging.logger { CompletedToPendingItemStateService::class.java.name }

    fun processCompletedToPendingItemState(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        locationId: Long,
        listId: UUID,
        itemIds: List<UUID>,
        retryState: RetryState
    ): Mono<RetryState> {
        return when {
            retryState.incompleteState() -> {
                logger.debug("From processCompletedToPendingItemState(), starting processing for listId: $listId, itemIds: $itemIds")
                searchItemsInCompletedCart(listId, itemIds)
                        .flatMap { processItemsStateChange(guestId, locationId, listId, retryState, it) }
                        .onErrorResume { Mono.just(retryState) }
                        .switchIfEmpty { Mono.just(retryState) }
            }
            retryState.partialCompleteState() -> {
                logger.debug("From processCompletedToPendingItemState(), starting processing of partially completed state for listId: $listId, itemIds: $itemIds")
                searchItemsInCompletedCart(listId, itemIds)
                        .flatMap { deleteItemsInCompletedList(guestId, listId, it.first, it.second, retryState) }
                        .onErrorResume { Mono.just(retryState) }
                        .switchIfEmpty { Mono.just(retryState) }
            }
            retryState.completeState() -> {
                logger.debug("From processCompletedToPendingItemState(), processing complete for listId: $listId, itemIds: $itemIds")
                Mono.just(retryState)
            }
            else -> {
                logger.error("Unknown step for guestId $guestId with listId $listId from processCompletedToPendingItemState()")
                retryState.createItemsInPendingList = true
                retryState.deleteItemsInCompletedList = true
                Mono.just(retryState)
            }
        }
    }

    /**
     *
     * Implements the functionality to get items to be updated to pending from the completed cart
     *
     */
    private fun searchItemsInCompletedCart(
        listId: UUID,
        itemIds: List<UUID>
    ): Mono<Pair<UUID, List<CartItemResponse>>> {
        return cartManager.getCompletedListCart(listId)
                .flatMap {
                    getItemsInCompletedCart(it.cartId!!, itemIds)
                }
    }

    private fun getItemsInCompletedCart(
        completedCartId: UUID,
        itemIds: List<UUID>
    ): Mono<Pair<UUID, List<CartItemResponse>>> {
        return cartManager.getCompletedListCartContents(completedCartId, true)
                .switchIfEmpty {
                    throw ResourceNotFoundException(AppErrorCodes.RESOURCE_NOT_FOUND_ERROR_CODE
                    (listOf("List: $completedCartId Item: $itemIds not found")))
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
                    Pair(completedCartId, cartItems)
                }
    }

    /**
     *
     * Implements the functionality to create the items in the pending list and delete the items in completed list
     *
     */
    private fun processItemsStateChange(
        guestId: String,
        locationId: Long,
        listId: UUID,
        retryState: RetryState,
        tuple: Pair<UUID, List<CartItemResponse>>
    ): Mono<RetryState> {
        return if (tuple.second.isNullOrEmpty()) {
            logger.error("From processItemsStateChange(), Items to update not found in completed list")
            Mono.just(retryState)
        } else {
            createItemsInPendingList(guestId, locationId, listId, tuple.second, retryState)
                    .flatMap { deleteItemsInCompletedList(guestId, listId, tuple.first, tuple.second, it) }
        }
    }

    /**
     *
     * Implements the functionality to create the multiple items in the pending list
     *
     */
    private fun createItemsInPendingList(
        guestId: String,
        locationId: Long,
        listId: UUID,
        items: List<CartItemResponse>,
        retryState: RetryState
    ): Mono<RetryState> {
        val newItems = items.map { toListItemRequestTO(it) }
        logger.debug("From createItemsInPendingList(), creating items in pending list")

        return addMultiItemsManager.processAddMultiItems(guestId, locationId, listId, listId, LIST_ITEM_STATE.PENDING, newItems)
                .map {
                    retryState.createItemsInPendingList = true
                    retryState
                }
                .onErrorResume { Mono.just(retryState) }
    }

    /**
     *
     * Implements the functionality to delete the multiple items in the completed list
     *
     */
    private fun deleteItemsInCompletedList(
        guestId: String,
        listId: UUID,
        completedCartId: UUID,
        items: List<CartItemResponse>,
        retryState: RetryState
    ): Mono<RetryState> {
        if (!retryState.createItemsInPendingList) {
            logger.debug("From deleteItemsInCompletedList(),Exception creating items in pending list")
            return Mono.just(retryState)
        } else {
            logger.debug("From deleteItemsInCompletedList(), deleting items in completed list")
            return deleteCartItemsManager.deleteMultipleCartItems(guestId, listId,
                    completedCartId, items.toTypedArray())
                    .map {
                        retryState.deleteItemsInCompletedList = true
                        retryState
                    }
                    .switchIfEmpty {
                        logger.error("From deleteItemInCompletedList(), Item already deleted from completed list")
                        retryState.deleteItemsInCompletedList = true
                        Mono.just(retryState)
                    }
                    .onErrorResume {
                        logger.error("Exception deleting cart items in completed list from " +
                                "deleteItemsInCompletedList() with [error: ${it.message}]", it)
                        Mono.just(retryState)
                    }
        }
    }

    data class RetryState(
        var createItemsInPendingList: Boolean = false,
        var deleteItemsInCompletedList: Boolean = false
    ) {
        fun completeState(): Boolean {
            return createItemsInPendingList && deleteItemsInCompletedList
        }

        fun incompleteState(): Boolean {
            return !createItemsInPendingList && !deleteItemsInCompletedList
        }

        fun partialCompleteState(): Boolean {
            return createItemsInPendingList && !deleteItemsInCompletedList
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
