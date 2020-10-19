package com.tgt.lists.atlas.api.async

import com.tgt.lists.cart.transport.AbandonAfterDuration
import com.tgt.lists.cart.transport.CartPutRequest
import com.tgt.lists.cart.transport.CartResponse
import com.tgt.lists.atlas.api.domain.CartManager
import com.tgt.lists.atlas.api.util.CartManagerName
import io.micronaut.context.annotation.Value
import mu.KotlinLogging
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AbandonCartDurationService(
    @CartManagerName("AbandonCartDurationService") @Inject private val cartManager: CartManager,
    @Value("\${list.abandon-after-duration-in-days}") private val abandonAfterDurationInDays: Long? = 0, // default value of 0 days
    @Value("\${list.features.two-carts}") private val isTwoCartsEnabled: Boolean
) {

    private val logger = KotlinLogging.logger {}

    val abandonAfterDuration: AbandonAfterDuration by lazy {
        AbandonAfterDuration(BigDecimal.valueOf(abandonAfterDurationInDays!!), AbandonAfterDuration.Type.DAYS)
    }

    fun setAbandonCartDuration(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        listId: UUID,
        abandonCalculationRefTime: LocalDateTime = LocalDateTime.now()
    ): Mono<Boolean> {

        logger.debug("[setAbandonCartDuration] guestId: $guestId, listId: $listId")

        return calculateAbandonCartDuration(listId, abandonCalculationRefTime)
                .flatMap { updateCart(listId, it) }
                .switchIfEmpty {
                    logger.error("No pending cart found $listId")
                    Mono.just(false)
                }
                .onErrorResume {
                    logger.error("Error while getting pending cart with id $listId", it)
                    Mono.just(false)
                }
    }

    private fun calculateAbandonCartDuration(
        listId: UUID,
        abandonCalculationRefTime: LocalDateTime
    ): Mono<Pair<CartResponse, AbandonAfterDuration>> {
        return cartManager.getListCartContents(listId)
                .map {
                    // AbandonAfterDuration time period is calculated from the createdTs, so we calculate the
                    // AbandonAfterDuration by adding the time elapsed since the cart was created and
                    // abandonAfterDurationInDays that we want the cart to be active in future
                    val cartCreatedDate = it.cart?.createdAt!!
                    val activeCartDuration = ChronoUnit.DAYS.between(cartCreatedDate, abandonCalculationRefTime)
                    Pair(it.cart!!, AbandonAfterDuration(abandonAfterDuration.value!!.plus(BigDecimal.valueOf(activeCartDuration)),
                            AbandonAfterDuration.Type.DAYS))
                }
    }

    private fun updateCart(
        listId: UUID,
        tuple: Pair<CartResponse, AbandonAfterDuration>
    ): Mono<Boolean> {
        val abandonAfterDuration = tuple.second
        val existingAbandonAfterDurationValue = tuple.first.abandonAfterDuration?.value ?: 0

        if (abandonAfterDuration.value == existingAbandonAfterDurationValue) {
            return Mono.just(true) // Not updating abandonAfterDuration if the calculated value is same as the existing value
        }

        if (abandonAfterDuration.value == null) {
            logger.error("Exception updating AbandonCartDuration")
            return Mono.just(false)
        }

        return updateAbandonCartDuration(listId, abandonAfterDuration)
                .zipWith(updateCompletedCart(listId, abandonAfterDuration))
                .flatMap {
                    if (it.t1 == false || it.t2 == false) {
                        logger.error("Exception updating AbandonCartDuration")
                        Mono.just(false)
                    } else {
                        Mono.just(true)
                    }
                }
    }

    private fun updateCompletedCart(
        listId: UUID,
        abandonAfterDuration: AbandonAfterDuration
    ): Mono<Boolean> {
        return if (isTwoCartsEnabled) {
            cartManager.getCompletedListCart(listId)
                    .flatMap {
                        updateAbandonCartDuration(it.cartId!!, abandonAfterDuration)
                    }
                    .switchIfEmpty {
                        Mono.just(false)
                    }
        } else {
            Mono.just(true)
        }
    }

    private fun updateAbandonCartDuration(
        listId: UUID,
        abandonAfterDuration: AbandonAfterDuration
    ): Mono<Boolean> {
        return cartManager.updateCart(cartId = listId,
                cartPutRequest = CartPutRequest(abandonAfterDuration = abandonAfterDuration))
                .onErrorResume {
                    Mono.just(CartResponse())
                }.map { it.cartId != null }
    }
}
