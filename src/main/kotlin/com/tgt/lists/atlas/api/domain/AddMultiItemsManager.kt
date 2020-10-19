package com.tgt.lists.atlas.api.domain

import com.tgt.lists.cart.transport.AddMultiCartItemsRequest
import com.tgt.lists.cart.transport.CartItemResponse
import com.tgt.lists.atlas.api.transport.ListItemRequestTO
import com.tgt.lists.atlas.api.transport.toCartItemsPostRequest
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddMultiItemsManager(
    @Inject private val deduplicationManager: DeduplicationManager,
    @Inject private val createCartItemsManager: CreateCartItemsManager
) {

    private val logger = KotlinLogging.logger { AddMultiItemsManager::class.java.name }

    /**
     *
     * Implements the functionality to add multiple cart items. Items already existing in the list
     * are updated and new items are added to the list
     *
     */
    fun processAddMultiItems(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        locationId: Long,
        listId: UUID,
        cartId: UUID,
        listItemState: LIST_ITEM_STATE,
        newItems: List<ListItemRequestTO>
    ): Mono<List<CartItemResponse>> {
        val newItemsMap = newItems.map {
            it.itemRefId to it
        }.toMap()

        return deduplicationManager.updateDuplicateItems(listId, cartId, locationId, newItemsMap, listItemState)
                .flatMap { addNewItems(guestId, locationId, listId, cartId, listItemState, newItemsMap, it.first, it.second, it.third) }
    }

    /**
     *
     * Implements the functionality to add multiple cart items after completing the deduplication process.
     *
     */
    private fun addNewItems(
        guestId: String,
        locationId: Long,
        listId: UUID,
        cartId: UUID,
        listItemState: LIST_ITEM_STATE,
        newItemsMap: Map<String, ListItemRequestTO>,
        existingItems: List<CartItemResponse>,
        updatedItems: List<CartItemResponse>,
        duplicateItemsMap: MutableMap<String, List<CartItemResponse>>
    ): Mono<List<CartItemResponse>> {
        val itemsToCreate = newItemsMap.filter { !duplicateItemsMap.contains(it.key) }
                .map { toCartItemsPostRequest(cartId, locationId, it.value, listItemState) }
        val cartItems = arrayListOf<CartItemResponse>()

        if (itemsToCreate.isNullOrEmpty()) {
            logger.debug("From addNewItems(), No items to add")
            cartItems.addAll(updatedItems)
            return Mono.just(cartItems)
        }

        return createCartItemsManager.createMultiListItem(guestId, locationId, listId,
                AddMultiCartItemsRequest(cartId = cartId, cartItems = itemsToCreate.toTypedArray()), existingItems)
                .map {
                    cartItems.addAll(it)
                    cartItems.addAll(updatedItems)
                    cartItems
                }
    }
}
