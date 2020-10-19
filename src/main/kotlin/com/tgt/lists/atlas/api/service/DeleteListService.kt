package com.tgt.lists.atlas.api.service

import com.tgt.lists.cart.transport.CartDeleteRequest
import com.tgt.lists.cart.transport.CartDeleteResponse
import com.tgt.lists.cart.transport.CartResponse
import com.tgt.lists.atlas.api.domain.CartManager
import com.tgt.lists.atlas.api.domain.EventPublisher
import com.tgt.lists.atlas.api.transport.ListDeleteResponseTO
import com.tgt.lists.atlas.api.util.CartManagerName
import com.tgt.lists.atlas.api.util.getListMetaDataFromCart
import com.tgt.lists.atlas.api.util.getUserMetaDataFromCart
import com.tgt.lists.atlas.kafka.model.DeleteListNotifyEvent
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeleteListService(
    @CartManagerName("DeleteListService") @Inject private val cartManager: CartManager,
    @Inject private val eventPublisher: EventPublisher
) {
    private val logger = KotlinLogging.logger {}

    fun deleteList(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        listId: UUID
    ): Mono<ListDeleteResponseTO> {

        logger.debug("[deleteList] guestId: $guestId, listId: $listId")

        return getCarts(guestId, listId).flatMap { process(guestId, listId, it[0], it[1]) }
    }

    private fun getCarts(guestId: String, listId: UUID): Mono<List<CartResponse?>> {
        return cartManager.getAllCarts(guestId = guestId)
            .switchIfEmpty(Mono.just(listOf()))
            .map {
                val cartResponses = it
                listOf(cartResponses.firstOrNull { it.cartId!! == listId },
                    cartResponses.firstOrNull { it.cartNumber == listId.toString() })
            }
    }

    private fun process(
        guestId: String,
        listId: UUID,
        primaryCartResponse: CartResponse?,
        secondaryCartResponse: CartResponse?
    ): Mono<ListDeleteResponseTO> {
        return doDelete(guestId, secondaryCartResponse)
            .flatMap { doDelete(guestId, primaryCartResponse) }
            .map { ListDeleteResponseTO(listId) }
    }

    private fun doDelete(guestId: String, cartResponse: CartResponse?): Mono<CartDeleteResponse> {
        if (cartResponse == null) {
            return Mono.just(CartDeleteResponse())
        }

        return cartManager.deleteCart(CartDeleteRequest(cartId = cartResponse.cartId, forceDeletion = false))
            .zipWhen {
                val listMetaDataTO = getListMetaDataFromCart(cartResponse.metadata)
                val userMetaDataTO = getUserMetaDataFromCart(cartResponse.metadata)
                eventPublisher.publishEvent(DeleteListNotifyEvent.getEventType(),
                    DeleteListNotifyEvent(guestId, cartResponse.cartId!!, cartResponse.cartSubchannel!!, cartResponse.tenantCartName!!,
                            listMetaDataTO, userMetaDataTO?.userMetaData), guestId)
            }.map { it.t1 }
    }
}
