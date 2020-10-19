package com.tgt.lists.atlas.api.service

import com.tgt.lists.common.components.exception.BadRequestException
import com.tgt.lists.atlas.api.domain.CartManager
import com.tgt.lists.atlas.api.domain.EventPublisher
import com.tgt.lists.atlas.api.transport.EditListSortOrderRequestTO
import com.tgt.lists.atlas.api.util.AppErrorCodes
import com.tgt.lists.atlas.api.util.CartManagerName
import com.tgt.lists.atlas.kafka.model.EditListSortOrderActionEvent
import mu.KotlinLogging
import reactor.core.publisher.Mono
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EditListSortOrderService(
    @Inject private val eventPublisher: EventPublisher,
    @CartManagerName("EditListSortOrderService") @Inject private val cartManager: CartManager
) {
    private val logger = KotlinLogging.logger {}

    fun editListPosition(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        editListSortOrderRequestTO: EditListSortOrderRequestTO
    ): Mono<Boolean> {

        logger.debug("[editListPosition] guestId: $guestId, primaryListId: ${editListSortOrderRequestTO.primaryListId}, secondaryListId: ${editListSortOrderRequestTO.secondaryListId}")

        return cartManager.getListCartContents(editListSortOrderRequestTO.primaryListId)
                .flatMap {
                    cartManager.getAllPendingCarts(it.cart!!.guestId!!)
                            .map {
                                val isAuthorisedPrimaryList = it.find { it.cartId == editListSortOrderRequestTO.primaryListId }
                                val isAuthorisedSecondaryList = it.find { it.cartId == editListSortOrderRequestTO.secondaryListId }
                                if (isAuthorisedPrimaryList == null || isAuthorisedSecondaryList == null) {
                                    throw BadRequestException(AppErrorCodes.BAD_REQUEST_ERROR_CODE(listOf("user is not authorized to do this sort")))
                                }
                                editListSortOrderRequestTO
                            }
                            .flatMap {
                                if (it.primaryListId == it.secondaryListId)
                                    Mono.just(true)
                                else
                                    eventPublisher.publishEvent(EditListSortOrderActionEvent.getEventType(), EditListSortOrderActionEvent(guestId,
                                            it), it.primaryListId.toString())
                                            .map { true }
                            }
                }
    }
}
