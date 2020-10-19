package com.tgt.lists.atlas.api.domain

import com.tgt.lists.cart.transport.AddMultiCartItemsRequest
import com.tgt.lists.cart.transport.CartItemResponse
import com.tgt.lists.atlas.api.transport.ListItemRequestTO
import com.tgt.lists.atlas.api.transport.toCartItemsPostRequest
import com.tgt.lists.atlas.api.util.CartManagerName
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.api.util.getListItemMetaDataFromCart
import com.tgt.lists.atlas.api.util.getUserItemMetaDataFromCart
import com.tgt.lists.atlas.kafka.model.CreateListItemNotifyEvent
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreateCartItemsManager(
    @CartManagerName("CreateCartItemsManager") @Inject private val cartManager: CartManager,
    @Inject private val eventPublisher: EventPublisher
) {

    fun createListItem(
        guestId: String,
        listId: UUID,
        locationId: Long,
        listItemRequest: ListItemRequestTO,
        listItemState: LIST_ITEM_STATE? = null
    ): Mono<CartItemResponse> {
        return cartManager.createCartItem(cartItemsRequest = toCartItemsPostRequest(listId, locationId,
            listItemRequest.validate(), listItemState ?: LIST_ITEM_STATE.PENDING))
            .zipWhen {
                val listItemMetaDataTO = getListItemMetaDataFromCart(it.metadata)
                val userItemMetaDataTO = getUserItemMetaDataFromCart(it.metadata)
                eventPublisher.publishEvent(CreateListItemNotifyEvent.getEventType(),
                    CreateListItemNotifyEvent(guestId, listId, it.cartItemId!!,
                            it.tcin, it.tenantItemName, it.itemAddChannel, it.requestedQuantity,
                            listItemMetaDataTO!!, userItemMetaDataTO?.userMetaData),
                    it.cartId.toString()) // this will never throw an error
            }.map { it.t1 }
    }

    fun createMultiListItem(
        guestId: String,
        locationId: Long,
        listId: UUID,
        addMultiCartItemsRequest: AddMultiCartItemsRequest,
        existingItems: List<CartItemResponse>
    ): Mono<List<CartItemResponse>> {
        return cartManager.addMultiCartItems(addMultiCartItemsRequest = addMultiCartItemsRequest,
                existingCartItemIds = existingItems.map { it.cartItemId!! }.toSet())
                .flatMapMany { Flux.fromIterable(it) }
                .flatMap { cartItemResponse ->
                    val listItemMetaDataTO = getListItemMetaDataFromCart(cartItemResponse.metadata)
                    val userItemMetaDataTO = getUserItemMetaDataFromCart(cartItemResponse.metadata)
                    eventPublisher.publishEvent(CreateListItemNotifyEvent.getEventType(),
                            CreateListItemNotifyEvent(guestId, listId, cartItemResponse.cartItemId!!,
                                    cartItemResponse.tcin, cartItemResponse.tenantItemName,
                                    cartItemResponse.itemAddChannel, cartItemResponse.requestedQuantity,
                                    listItemMetaDataTO!!, userItemMetaDataTO?.userMetaData),
                            listId.toString()).map { cartItemResponse }
                }.collectList()
    }
}
