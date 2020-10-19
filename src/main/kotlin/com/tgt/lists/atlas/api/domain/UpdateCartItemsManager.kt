package com.tgt.lists.atlas.api.domain

import com.tgt.lists.cart.transport.CartItemResponse
import com.tgt.lists.cart.transport.CartItemUpdateRequest
import com.tgt.lists.cart.transport.UpdateMultiCartItemsRequest
import com.tgt.lists.atlas.api.util.CartManagerName
import com.tgt.lists.atlas.api.util.getListItemMetaDataFromCart
import com.tgt.lists.atlas.api.util.getUserItemMetaDataFromCart
import com.tgt.lists.atlas.kafka.model.UpdateListItemNotifyEvent
import mu.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateCartItemsManager(
    @CartManagerName("UpdateCartItemsManager") @Inject private val cartManager: CartManager,
    @Inject private val eventPublisher: EventPublisher
) {

    private val logger = KotlinLogging.logger { UpdateCartItemsManager::class.java.name }

    fun updateCartItem(
        guestId: String,
        cartId: UUID,
        cartItemId: UUID,
        cartItemUpdateRequest: CartItemUpdateRequest
    ): Mono<CartItemResponse> {
        return cartManager.updateCartItem(cartItemId = cartItemId, cartItemUpdateRequest = cartItemUpdateRequest)
                .zipWhen {
                    val listItemMetaDataTO = getListItemMetaDataFromCart(it.metadata)
                    val userItemMetaDataTO = getUserItemMetaDataFromCart(it.metadata)
                    eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(),
                            UpdateListItemNotifyEvent(guestId, cartId, cartItemId,
                                    it.tcin, it.tenantItemName, it.requestedQuantity,
                                    listItemMetaDataTO!!, userItemMetaDataTO?.userMetaData),
                            it.cartId.toString())
                }.map { it.t1 }
    }

    fun updateMultiCartItem(
        guestId: String,
        listId: UUID,
        updateMultiCartItemsRequest: UpdateMultiCartItemsRequest
    ): Mono<List<CartItemResponse>> {
        // fieldGroups must be added for a response
        return cartManager.updateMultiCartItems(updateMultiCartItemsUpdateRequest = updateMultiCartItemsRequest)
                .map {
                    val updatedCartItems = arrayListOf<CartItemResponse>()
                    val cartItems = it.cartItems?.toList() ?: emptyList()
                    updateMultiCartItemsRequest.cartItems?.toList()?.stream()?.forEach { updatedItem ->
                        val item = cartItems.firstOrNull { it.cartItemId == updatedItem.cartItemId }
                        if (item != null) { updatedCartItems.add(item) }
                    }
                    updatedCartItems
                }
                .flatMapMany { Flux.fromIterable(it) }
                .flatMap { cartItemResponse ->
                    val listItemMetaDataTO = getListItemMetaDataFromCart(cartItemResponse.metadata)
                    val userItemMetaDataTO = getUserItemMetaDataFromCart(cartItemResponse.metadata)
                    eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(),
                            UpdateListItemNotifyEvent(guestId, listId, cartItemResponse.cartItemId!!,
                                    cartItemResponse.tcin, cartItemResponse.tenantItemName, cartItemResponse.requestedQuantity,
                                    listItemMetaDataTO!!, userItemMetaDataTO?.userMetaData),
                            listId.toString()).map { cartItemResponse }
                }.collectList()
    }
}
