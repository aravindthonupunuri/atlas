package com.tgt.lists.atlas.api.service

import com.tgt.lists.common.components.exception.BadRequestException
import com.tgt.lists.atlas.api.domain.CartManager
import com.tgt.lists.atlas.api.domain.EventPublisher
import com.tgt.lists.atlas.api.transport.EditItemSortOrderRequestTO
import com.tgt.lists.atlas.api.util.AppErrorCodes
import com.tgt.lists.atlas.api.util.CartManagerName
import com.tgt.lists.atlas.kafka.model.EditListItemSortOrderActionEvent
import mu.KotlinLogging
import reactor.core.publisher.Mono
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EditItemSortOrderService(
    @Inject private val eventPublisher: EventPublisher,
    @CartManagerName("EditItemSortOrderService") @Inject private val cartManager: CartManager
) {
    private val logger = KotlinLogging.logger {}

    fun editItemPosition(editItemSortOrderRequestTO: EditItemSortOrderRequestTO): Mono<Boolean> {

        logger.debug("[editItemPosition] listId: ${editItemSortOrderRequestTO.listId}, primaryItemId: ${editItemSortOrderRequestTO.primaryItemId}, secondaryItemId: ${editItemSortOrderRequestTO.secondaryItemId}")

        return cartManager.getListCartContents(editItemSortOrderRequestTO.listId, true)
                .map {
                    val isAuthorisedPrimaryItem = it.cartItems?.find { it.cartItemId == editItemSortOrderRequestTO.primaryItemId }
                    val isAuthorisedSecondaryItem = it.cartItems?.find { it.cartItemId == editItemSortOrderRequestTO.secondaryItemId }
                    if (isAuthorisedPrimaryItem == null || isAuthorisedSecondaryItem == null) {
                        throw BadRequestException(AppErrorCodes.BAD_REQUEST_ERROR_CODE(listOf("user is not authorized to do this sort")))
                    }
                    editItemSortOrderRequestTO
                }
                .flatMap {
                    if (it.primaryItemId == it.secondaryItemId)
                        Mono.just(true)
                    else
                        eventPublisher.publishEvent(EditListItemSortOrderActionEvent.getEventType(),
                                EditListItemSortOrderActionEvent(editItemSortOrderRequestTO), editItemSortOrderRequestTO.listId.toString()).map { true }
                }
    }
}
