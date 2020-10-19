package com.tgt.lists.atlas.api.domain

import com.tgt.lists.cart.transport.CartPutRequest
import com.tgt.lists.cart.transport.CartResponse
import com.tgt.lists.atlas.api.util.CartManagerName
import com.tgt.lists.atlas.api.util.GuestId
import com.tgt.lists.atlas.api.util.getListMetaDataFromCart
import com.tgt.lists.atlas.api.util.getUserMetaDataFromCart
import com.tgt.lists.atlas.kafka.model.UpdateListNotifyEvent
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateCartManager(
    @CartManagerName("UpdateCartManager") @Inject private val cartManager: CartManager,
    @Inject private val eventPublisher: EventPublisher
) {

    fun updateCart(
        guestId: GuestId,
        cartId: UUID,
        cartPutRequest: CartPutRequest
    ): Mono<CartResponse> {
        return cartManager.updateCart(cartId = cartId, cartPutRequest = cartPutRequest)
            .zipWhen {
                val listMetaDataTO = getListMetaDataFromCart(it.metadata)
                val userMetaDataTO = getUserMetaDataFromCart(it.metadata)
                eventPublisher.publishEvent(UpdateListNotifyEvent.getEventType(),
                    UpdateListNotifyEvent(guestId, cartId, it.cartSubchannel!!, cartPutRequest.tenantCartName, listMetaDataTO, userMetaDataTO?.userMetaData), guestId)
            }.map { it.t1 }
    }
}
