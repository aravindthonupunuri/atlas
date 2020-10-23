package com.tgt.lists.atlas.api.domain

import com.tgt.lists.atlas.api.util.CartManagerName
import com.tgt.lists.atlas.api.util.getUserItemMetaDataFromCart
import com.tgt.lists.atlas.kafka.model.DeleteListItemNotifyEvent
import com.tgt.lists.atlas.kafka.model.MultiDeleteListItem
import com.tgt.lists.cart.transport.*
import io.micronaut.context.annotation.Value
import mu.KotlinLogging
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeleteCartItemsManager(
    @CartManagerName("DeleteCartItemsManager") @Inject private val cartManager: CartManager,
    @Inject private val eventPublisher: EventPublisher,
    @Value("\${list.features.two-carts}") private val isTwoCartsEnabled: Boolean
) {

    private val logger = KotlinLogging.logger { DeleteCartItemsManager::class.java.name }

    fun deleteAllCartItems(guestId: String, listId: UUID): Mono<DeleteMultiCartItemsResponse> {

        logger.debug("[deleteAllCartItems] guestId: $guestId, listId: $listId")

        return if (isTwoCartsEnabled) {
            return deleteCompletedCartItems(guestId, listId)
                    .zipWhen { processDeleteCartItems(guestId, listId) }
                    .map {
                        val deletedItemIds = mutableListOf<UUID>()
                        if (!it.t1.deletedCartItemIds.isNullOrEmpty()) {
                            deletedItemIds.addAll(it.t1.deletedCartItemIds!!)
                        }
                        if (!it.t2.deletedCartItemIds.isNullOrEmpty()) {
                            deletedItemIds.addAll(it.t2.deletedCartItemIds!!)
                        }

                        val failedItemIds = mutableListOf<UUID>()
                        if (!it.t1.failedCartItemIds.isNullOrEmpty()) {
                            failedItemIds.addAll(it.t1.failedCartItemIds!!)
                        }
                        if (!it.t2.failedCartItemIds.isNullOrEmpty()) {
                            failedItemIds.addAll(it.t2.failedCartItemIds!!)
                        }
                        DeleteMultiCartItemsResponse(listId, deletedItemIds.toTypedArray(), failedItemIds.toTypedArray())
                    }
        } else {
            processDeleteCartItems(guestId, listId)
                    .map {
                        val deletedItemIds = mutableListOf<UUID>()
                        if (!it.deletedCartItemIds.isNullOrEmpty()) {
                            deletedItemIds.addAll(it.deletedCartItemIds!!)
                        }

                        val failedItemIds = mutableListOf<UUID>()
                        if (!it.failedCartItemIds.isNullOrEmpty()) {
                            failedItemIds.addAll(it.failedCartItemIds!!)
                        }
                        DeleteMultiCartItemsResponse(listId, deletedItemIds.toTypedArray(), failedItemIds.toTypedArray())
                    }
        }
    }

    fun deleteCompletedCartItems(guestId: String, listId: UUID): Mono<DeleteMultiCartItemsResponse> {
        if (!isTwoCartsEnabled) {
            throw RuntimeException("Delete completed-item not available with feature isTwoCartsEnabled=false")
        }
        return deleteCompletedCartItemsByIds(guestId, listId)
                .map { DeleteMultiCartItemsResponse(listId, it.second.deletedCartItemIds, it.second.failedCartItemIds) }
    }

    private fun deleteCompletedCartItemsByIds(
        guestId: String,
        listId: UUID,
        listItemIds: Set<UUID>? = null
    ): Mono<Pair<Set<UUID>?, DeleteMultiCartItemsResponse>> {
        if (!isTwoCartsEnabled) {
            throw RuntimeException("Delete completed items not available with feature isTwoCartsEnabled=false")
        }
        return cartManager.getCompletedListCart(listId)
                .flatMap { cartResponse ->
                    val completedCartId: UUID? = cartResponse.cartId
                    if (completedCartId == null) {
                        Mono.just(Pair(listItemIds, DeleteMultiCartItemsResponse()))
                    } else {
                        processDeleteCartItemsByIds(guestId, listId, completedCartId, listItemIds)
                    }
                }
                .switchIfEmpty {
                    Mono.just(Pair(listItemIds, DeleteMultiCartItemsResponse()))
                }
    }

    private fun processDeleteCartItemsByIds(
        guestId: String,
        listId: UUID,
        cartId: UUID,
        listItemIds: Set<UUID>? = null
    ): Mono<Pair<Set<UUID>?, DeleteMultiCartItemsResponse>> {
        return cartManager.getListCartContents(cartId, true)
                .switchIfEmpty { Mono.just(CartContentsResponse()) }
                .flatMap {
                    it.cartItems?.let { cartItems ->
                        val filteredItems = cartItems.filter { cartItem -> listItemIds.isNullOrEmpty() ||
                                listItemIds.contains(cartItem.cartItemId) }.toTypedArray()
                        val filteredItemIds = filteredItems.map { cartItem -> cartItem.cartItemId!! }.toSet()
                        val notFoundIds = listItemIds?.filter { listItemId -> !filteredItemIds.contains(listItemId) }?.toSet()
                        deleteMultipleCartItems(guestId, listId, cartId, filteredItems).map { deleteResponse -> Pair(notFoundIds, deleteResponse) }
                    } ?: Mono.just(Pair(listItemIds, DeleteMultiCartItemsResponse()))
                }
    }

    fun deleteMultipleCartItems(
        guestId: String,
        listId: UUID,
        cartId: UUID,
        cartItemResponses: Array<CartItemResponse>
    ): Mono<DeleteMultiCartItemsResponse> {
        return cartManager.deleteMultiCartItems(deleteMultiCartItemsRequest = DeleteMultiCartItemsRequest(cartId = cartId,
                cartItemIds = cartItemResponses.map { it.cartItemId!! }.toTypedArray(), forceDeletion = true))
                .flatMap { publishDeleteListItemEvent(guestId, listId, cartItemResponses, it) }
    }

    private fun publishDeleteListItemEvent(
        guestId: String,
        listId: UUID,
        cartItemResponses: Array<CartItemResponse>,
        deleteMultiCartItemsResponse: DeleteMultiCartItemsResponse
    ): Mono<DeleteMultiCartItemsResponse> {
        return deleteMultiCartItemsResponse.deletedCartItemIds
                .takeIf { !it.isNullOrEmpty() }
                ?.let { deletedCartItemIds ->
                    val deletedCartIds = deletedCartItemIds.toSet()
                    val multiDeleteItems = cartItemResponses.filter { deletedCartIds.contains(it.cartItemId) }
                            .map {
                                val userItemMetaDataTO = getUserItemMetaDataFromCart(it.metadata)
                                MultiDeleteListItem(it.cartItemId!!, it.tcin, it.tenantItemName, it.requestedQuantity,
                                        null, userItemMetaDataTO?.userMetaData) }
                            .toList()
                    eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(),
                            DeleteListItemNotifyEvent(guestId, listId, multiDeleteItems), listId.toString())
                            .map { deleteMultiCartItemsResponse }
                } ?: Mono.just(deleteMultiCartItemsResponse)
    }

    fun processDeleteCartItems(guestId: String, listId: UUID): Mono<DeleteMultiCartItemsResponse> {
        return processDeleteCartItemsByIds(guestId, listId, listId).map { it.second }
    }

    fun deleteCartItem(guestId: String, listId: UUID, listItemId: UUID): Mono<CartItemDeleteResponse> {
        return cartManager.getCartItem(cartItemId = listItemId, cartId = listId)
            .switchIfEmpty {
                if (isTwoCartsEnabled)
                    cartManager.getItemInCompletedCart(listId, listItemId)
                else
                    Mono.empty()
            }
            .flatMap { deleteCartItem(guestId, listId, it.cartId!!, it) }
    }

    private fun deleteCartItem(
        guestId: String,
        listId: UUID,
        cartId: UUID,
        cartItemsReponse: CartItemResponse
    ): Mono<CartItemDeleteResponse> {
        return cartManager.deleteCartItem(deleteCartItemRequest = DeleteCartItemRequest(cartId = cartId,
                cartItemId = cartItemsReponse.cartItemId!!, forceDeletion = true))
                .flatMap { cartItemDeleteReponse ->
                    val userItemMetaDataTO = getUserItemMetaDataFromCart(cartItemsReponse.metadata)
                    val multiDeleteItems = listOf(MultiDeleteListItem(cartItemsReponse.cartItemId!!,
                            cartItemsReponse.tcin, cartItemsReponse.tenantItemName, cartItemsReponse.requestedQuantity,
                            null, userItemMetaDataTO?.userMetaData))
                    eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(),
                            DeleteListItemNotifyEvent(guestId, listId, multiDeleteItems), listId.toString()).map { cartItemDeleteReponse }
                }
    }

    fun deleteCartItems(guestId: String, listId: UUID, listItemIds: Set<UUID>): Mono<DeleteMultiCartItemsResponse> {
        return processDeleteCartItemsByIds(guestId, listId, listId, listItemIds)
            .flatMap {
                if (it.first.isNullOrEmpty()) {
                    Mono.just(it.second)
                } else {
                    val pendingCartDeleteResponse = it.second
                    if (isTwoCartsEnabled) {
                        deleteCompletedCartItemsByIds(guestId, listId, it.first)
                                .flatMap { completedCartDeleteResponse ->
                                    val successIds = mutableListOf<UUID>()
                                    if (!pendingCartDeleteResponse.deletedCartItemIds.isNullOrEmpty()) {
                                        successIds.addAll(pendingCartDeleteResponse.deletedCartItemIds!!)
                                    }
                                    if (!completedCartDeleteResponse.first.isNullOrEmpty()) {
                                        successIds.addAll(completedCartDeleteResponse.first!!)
                                    }
                                    if (!completedCartDeleteResponse.second.deletedCartItemIds.isNullOrEmpty()) {
                                        successIds.addAll(completedCartDeleteResponse.second.deletedCartItemIds!!)
                                    }

                                    val failedIds = mutableListOf<UUID>()
                                    if (!pendingCartDeleteResponse.failedCartItemIds.isNullOrEmpty()) {
                                        failedIds.addAll(pendingCartDeleteResponse.failedCartItemIds!!)
                                    }
                                    if (!completedCartDeleteResponse.second.failedCartItemIds.isNullOrEmpty()) {
                                        failedIds.addAll(completedCartDeleteResponse.second.failedCartItemIds!!)
                                    }

                                    Mono.just(DeleteMultiCartItemsResponse(listId,
                                            deletedCartItemIds = successIds.toTypedArray(),
                                            failedCartItemIds = failedIds.toTypedArray()))
                                }
                    } else {
                        val successIds = mutableListOf<UUID>()
                        if (!pendingCartDeleteResponse.deletedCartItemIds.isNullOrEmpty()) {
                            successIds.addAll(pendingCartDeleteResponse.deletedCartItemIds!!)
                        }

                        val failedIds = mutableListOf<UUID>()
                        if (!pendingCartDeleteResponse.failedCartItemIds.isNullOrEmpty()) {
                            failedIds.addAll(pendingCartDeleteResponse.failedCartItemIds!!)
                        }

                        Mono.just(DeleteMultiCartItemsResponse(listId,
                                deletedCartItemIds = successIds.toTypedArray(),
                                failedCartItemIds = failedIds.toTypedArray()))
                    }
                }
            }
    }
}
