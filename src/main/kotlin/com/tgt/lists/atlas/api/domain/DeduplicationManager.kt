package com.tgt.lists.atlas.api.domain

import com.tgt.lists.cart.transport.*
import com.tgt.lists.common.components.exception.BadRequestException
import com.tgt.lists.atlas.api.transport.ListItemRequestTO
import com.tgt.lists.atlas.api.transport.ListItemUpdateRequestTO
import com.tgt.lists.atlas.api.transport.toCartItemUpdateRequest
import com.tgt.lists.atlas.api.util.AppErrorCodes
import com.tgt.lists.atlas.api.util.CartManagerName
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.api.util.getListItemMetaDataFromCart
import io.micronaut.context.annotation.Value
import mu.KotlinLogging
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.util.*
import java.util.stream.Collectors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeduplicationManager(
    @CartManagerName("DeduplicationManager") @Inject private val cartManager: CartManager,
    @Inject private val updateCartItemsManager: UpdateCartItemsManager,
    @Inject private val deleteCartItemsManager: DeleteCartItemsManager,
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
     */
    fun updateDuplicateItems(
        listId: UUID,
        cartId: UUID,
        locationId: Long,
        newItemsMap: Map<String, ListItemRequestTO>,
        itemState: LIST_ITEM_STATE
    ): Mono<Triple<List<CartItemResponse>, List<CartItemResponse>, MutableMap<String, List<CartItemResponse>>>> {

        return cartManager.getListCartContents(cartId, true)
                .flatMap {
                    processDedup(it.cart!!.guestId!!, listId, cartId, itemState, it, newItemsMap) }
                .switchIfEmpty {
                    logger.error("From updateDuplicateItems(), empty cart contents during dedup process")
                    Mono.just(Triple(emptyList<CartItemResponse>(), emptyList<CartItemResponse>(), mutableMapOf<String, List<CartItemResponse>>()))
                }
    }

    /**
     * Implements functionality to verify the final items count does not exceed the limit in both pending and
     * completed cart. It also updates the duplicate cart items already present in the list.
     */
    private fun processDedup(
        guestId: String,
        listId: UUID,
        cartId: UUID,
        itemState: LIST_ITEM_STATE,
        cartContents: CartContentsResponse,
        newItemsMap: Map<String, ListItemRequestTO>
    ): Mono<Triple<List<CartItemResponse>, List<CartItemResponse>, MutableMap<String, List<CartItemResponse>>>> {
        if (cartContents.cartItems.isNullOrEmpty()) {
            logger.debug("From processDedup(), No preexisting items in cart, so skipping dedup process")
            return Mono.just(Triple(emptyList(), emptyList(), mutableMapOf()))
        }
        val existingItems = cartContents.cartItems!!.toList() // items prsent in cart
        val newItems = newItemsMap.values.toList() // new items to be added

        return checkMaxItemsCount(guestId, listId, cartId, existingItems, newItems, itemState,
                getDuplicateItemsMap(existingItems, newItems, itemState))
                .flatMap { updateMultiItems(guestId, listId, cartId, newItemsMap, it) }
                .map { Triple(existingItems, it.first, it.second) }
    }

    /**
     * Implements functionality to update preexisting items and also delete cart items not deduped
     * already existing in the list.
     */
    private fun updateMultiItems(
        guestId: String,
        listId: UUID,
        cartId: UUID,
        newItemsMap: Map<String, ListItemRequestTO>,
        duplicateItemsMap: MutableMap<String, List<CartItemResponse>>
    ): Mono<Pair<List<CartItemResponse>, MutableMap<String, List<CartItemResponse>>>> {
        val dedupDataList = arrayListOf<DedupData>()

        duplicateItemsMap.asIterable().map {
            val newItem = newItemsMap[it.key]
            val duplicateItems = it.value

            if (newItem != null && !duplicateItems.isNullOrEmpty()) {
                val sortedExistingItemList = duplicateItems.sortedBy { it.createdAt }
                var updatedRequestedQuantity = 0
                var updatedItemNote = ""

                for (cartItemResponse in sortedExistingItemList) {
                    if (!cartItemResponse.notes.isNullOrEmpty()) {
                        updatedItemNote = if (updatedItemNote.isNotEmpty()) {
                            cartItemResponse.notes.toString() + "\n" + updatedItemNote
                        } else {
                            cartItemResponse.notes.toString()
                        }
                    }
                    updatedRequestedQuantity += cartItemResponse.requestedQuantity ?: 1
                }

                updatedRequestedQuantity += (newItem.requestedQuantity ?: 1)
                updatedItemNote = if (newItem.itemNote.isNullOrEmpty()) {
                    updatedItemNote
                } else {
                    newItem.itemNote + "\n" + updatedItemNote
                }

                val cartItemUpdateRequest = toCartItemUpdateRequest(sortedExistingItemList.first(),
                        cartId, sortedExistingItemList.first().cartItemId!!,
                        ListItemUpdateRequestTO(itemNote = updatedItemNote, requestedQuantity = updatedRequestedQuantity))
                val itemsToDelete = sortedExistingItemList
                        .filter { it.cartItemId != sortedExistingItemList.first().cartItemId }

                dedupDataList.add(DedupData(cartItemUpdateRequest, itemsToDelete))
            }
        }

        val totalItemsToDelete = arrayListOf<CartItemResponse>()
        dedupDataList.map { totalItemsToDelete.addAll(it.itemsToDelete) }

        val totalItemsToUpdate = dedupDataList.map { it.cartItemUpdateRequest }

        if (totalItemsToUpdate.isNullOrEmpty()) {
            logger.debug("From updateMultiItems(), No items to dedupe")
            return Mono.just(Pair(emptyList(), duplicateItemsMap))
        }

        val itemIdsToDelete = totalItemsToDelete.map { it.cartItemId!! }
        logger.debug("From updateMultiItems(), Items to Update: $totalItemsToUpdate," +
                " ItemIds to delete: $itemIdsToDelete , Deduplication process")

        return updateCartItemsManager.updateMultiCartItem(guestId, listId,
                UpdateMultiCartItemsRequest(cartId, totalItemsToUpdate.toTypedArray()))
                .zipWith(if (totalItemsToDelete.isNotEmpty()) {
                    deleteCartItemsManager.deleteMultipleCartItems(guestId, listId, cartId, totalItemsToDelete.toTypedArray())
                } else { Mono.just(DeleteMultiCartItemsResponse()) })
                .map { Pair(it.t1, duplicateItemsMap) }
    }

    private fun checkMaxItemsCount(
        guestId: String,
        listId: UUID,
        cartId: UUID,
        existingCartItems: List<CartItemResponse>,
        newItems: List<ListItemRequestTO>,
        itemState: LIST_ITEM_STATE,
        duplicateItemsMap: MutableMap<String, List<CartItemResponse>> // map of new item ref id to its duplicate existing items
    ): Mono<MutableMap<String, List<CartItemResponse>>> {
        // Validate max items count
        val existingItemsCount = existingCartItems.count() // count before dedup
        var count = 0
        duplicateItemsMap.keys.stream().forEach { count += (duplicateItemsMap[it]?.count() ?: 0) }
        val duplicateItemsCount = count // total no of duplicated items existing in cart
        val newItemsCount = newItems.count() // new items to be added

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
            val duplicateItems = arrayListOf<CartItemResponse>()
            duplicateItemsMap.values.forEach { duplicateItems.addAll(it) }
            // The result will consists of items to be deleted which are old.
            // existingCartItems -> Items that are present in the cart before dedup process.
            // Filter the duplicate items in existingCartItems since these items will be deleted during the deduplication process in updateMultiItems() method.
            // So the final items to be deleted since the list has reached max items count would be (existingCartItems - duplicate items).
            val result = existingCartItems.stream()
                    .filter { cartItemResponse -> duplicateItems.stream()
                            .noneMatch { it.cartItemId == cartItemResponse.cartItemId } }.collect(Collectors.toList())
            result.sortBy { it.updatedAt }
            logger.debug("Exceeding max items count in list, stale items to be delete count" +
                    " $itemsCountToDelete")

            return deleteCartItemsManager.deleteMultipleCartItems(guestId, listId, cartId,
                    result.take(itemsCountToDelete).toTypedArray())
                    .map { duplicateItemsMap }
                    .onErrorResume {
                        logger.error { it.message ?: it.cause?.message }
                        Mono.just(duplicateItemsMap)
                    }
        } else {
            return Mono.just(duplicateItemsMap)
        }
    }

    /**
     * Implements functionality to find the duplicates for every new item being added.
     */
    private fun getDuplicateItemsMap(
        existingCartItems: List<CartItemResponse>,
        newItems: List<ListItemRequestTO>,
        itemState: LIST_ITEM_STATE
    ): MutableMap<String, List<CartItemResponse>> {
        val itemIdentifierCartItemsMap = mutableMapOf<String, List<CartItemResponse>>()
        if (!isDedupeEnabled) {
            logger.debug("From getDuplicateItemsMap(), Dedupe turned off")
            return itemIdentifierCartItemsMap
        }

        newItems.stream().forEach { listItemRequest ->
            val preExistingItems = existingCartItems.filter { itemTypeFilter(it, listItemRequest) &&
                itemFilter(it, listItemRequest) && itemStateFilter(it, itemState) }
            if (!preExistingItems.isNullOrEmpty()) {
                itemIdentifierCartItemsMap[listItemRequest.itemRefId] = preExistingItems
            }
        }
        logger.debug("DuplicateItemsMap: keys ${itemIdentifierCartItemsMap.keys} " +
                "and values: ${itemIdentifierCartItemsMap.values.forEach {
                    it.forEach { it.cartItemId }
                }}")

        return itemIdentifierCartItemsMap
    }

    private fun itemTypeFilter(cartItemResponse: CartItemResponse, listItemRequest: ListItemRequestTO): Boolean {
        val cartItemMetadata = cartItemResponse.metadata
        val listItemMetadata = getListItemMetaDataFromCart(cartItemMetadata)
        val newItemType = listItemRequest.itemType
        val existingItemType = listItemMetadata?.itemType

        return newItemType == existingItemType
    }

    private fun itemFilter(cartItemResponse: CartItemResponse, listItemRequest: ListItemRequestTO): Boolean {
        return listItemRequest.itemRefId == cartItemResponse.tenantReferenceId
    }

    private fun itemStateFilter(cartItemResponse: CartItemResponse, itemState: LIST_ITEM_STATE): Boolean {
        val existingItemState = getListItemMetaDataFromCart(cartItemResponse.metadata)?.itemState
                ?: throw RuntimeException("Incorrect item state from itemStateFilter()")
        return existingItemState == itemState
    }

    data class DedupData(
        val cartItemUpdateRequest: CartItemUpdateRequest,
        val itemsToDelete: List<CartItemResponse>
    )
}
