package com.tgt.lists.atlas.api.service

import com.tgt.lists.cart.transport.CartItemResponse
import com.tgt.lists.common.components.exception.BadRequestException
import com.tgt.lists.atlas.api.domain.CartManager
import com.tgt.lists.atlas.api.domain.EventPublisher
import com.tgt.lists.atlas.api.domain.UpdateCartItemsManager
import com.tgt.lists.atlas.api.transport.ListItemResponseTO
import com.tgt.lists.atlas.api.transport.ListItemUpdateRequestTO
import com.tgt.lists.atlas.api.transport.toCartItemUpdateRequest
import com.tgt.lists.atlas.api.transport.toListItemResponseTO
import com.tgt.lists.atlas.api.util.*
import com.tgt.lists.atlas.api.util.AppErrorCodes.BAD_REQUEST_ERROR_CODE
import com.tgt.lists.atlas.kafka.model.CompletionItemActionEvent
import com.tgt.lists.atlas.kafka.model.PendingItemActionEvent
import io.micronaut.context.annotation.Value
import mu.KotlinLogging
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateListItemService(
    @CartManagerName("UpdateListItemService") @Inject private val cartManager: CartManager,
    @Inject private val eventPublisher: EventPublisher,
    @Inject private val updateCartItemsManager: UpdateCartItemsManager,
    @Value("\${list.features.two-carts}") private val isTwoCartsEnabled: Boolean
) {

    private val logger = KotlinLogging.logger {}

    /**
     *
     * The method implements the functionality to update list item
     *
     */
    fun updateListItem(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        locationId: Long,
        listId: UUID,
        listItemId: UUID,
        listItemUpdateRequest: ListItemUpdateRequestTO
    ): Mono<ListItemResponseTO> {

        logger.debug("[updateListItem] guestId: $guestId, listId: $listId, listItemId: $listItemId, locationId: $locationId")

        return if (listItemUpdateRequest.itemState != null) {
            if (!isTwoCartsEnabled) {
                Mono.error(BadRequestException(BAD_REQUEST_ERROR_CODE(listOf("Two carts not enabled"))))
            } else {
                logger.debug("From updateListItem(), Updating items state and item attributes")
                updateItemState(guestId, locationId, listId, listItemId, listItemUpdateRequest.validate())
            }
        } else {
            logger.debug("From updateListItem(), Updating only item attributes, no state change update")
            processUpdateListItem(guestId, listId, listItemId, listItemUpdateRequest.validate())
                    .map { toListItemResponseTO(it) }
        }
    }

    private fun updateItemState(
        guestId: String,
        locationId: Long,
        listId: UUID,
        listItemId: UUID,
        listItemUpdateRequest: ListItemUpdateRequestTO
    ): Mono<ListItemResponseTO> {
        return if (listItemUpdateRequest.onlyItemStateUpdate()) {
            logger.debug("From updateItemState(), onlyItemStateUpdate")
            getCartItem(listId, listItemId, listItemUpdateRequest)
                    .flatMap { tuple ->
                        val cartItemResponse = tuple.first
                        if (isItemValidForStateUpdate(cartItemResponse, listItemUpdateRequest.itemState)) {
                            publishItemStateEvent(guestId, locationId, listId, listItemId, listItemUpdateRequest)
                                    .map { toListItemResponseTO(cartItemResponse, listItemUpdateRequest.itemState) }
                        } else {
                            logger.debug("From updateItemState(), item not valid to update state")
                            Mono.just(toListItemResponseTO(cartItemResponse))
                        }
                    }
        } else {
            logger.debug("From updateItemState(), updating item attributes and items state")
            processUpdateListItem(guestId, listId, listItemId, listItemUpdateRequest.validate())
                    .flatMap { cartItemResponse ->
                        if (isItemValidForStateUpdate(cartItemResponse, listItemUpdateRequest.itemState)) {
                            publishItemStateEvent(guestId, locationId, listId, listItemId,
                                    listItemUpdateRequest)
                                    .onErrorResume {
                                        logger.error("Failure publishing item state kafka event into both" +
                                                " message bus topic and DLQ topic")
                                        Mono.just(true)
                                    }
                                    .map { toListItemResponseTO(cartItemResponse, listItemUpdateRequest.itemState) }
                        } else {
                            logger.debug("From updateItemState(), item not valid to update state")
                            Mono.just(toListItemResponseTO(cartItemResponse))
                        }
                    }
        }
    }

    /**
     *
     * The method implements the functionality to check if the current item state is not same as the state it
     * has to be updated. If the current state matches with the items state to which the item has to be updated then we
     * do not process item state change event.
     *
     */
    private fun isItemValidForStateUpdate(
        cartItemResponse: CartItemResponse,
        itemState: LIST_ITEM_STATE?
    ): Boolean {
        return getListItemMetaDataFromCart(cartItemResponse.metadata)?.itemState != itemState
    }

    /**
     *
     * The method implements the functionality to publish events for updating item state.
     *
     */
    private fun publishItemStateEvent(
        guestId: String,
        locationId: Long,
        listId: UUID,
        listItemId: UUID,
        listItemUpdateRequest: ListItemUpdateRequestTO
    ): Mono<Boolean> {
        return when (listItemUpdateRequest.itemState) {
            LIST_ITEM_STATE.PENDING -> eventPublisher.publishEvent(PendingItemActionEvent.getEventType(),
                    PendingItemActionEvent(guestId, locationId, listId, listOf(listItemId)), listId.toString()).then(Mono.just(true))
            LIST_ITEM_STATE.COMPLETED -> eventPublisher.publishEvent(CompletionItemActionEvent.getEventType(),
                    CompletionItemActionEvent(guestId, locationId, listId, listOf(listItemId)), listId.toString()).then(Mono.just(true))
            null -> {
                logger.debug("From publishItemStateEvent(), null itemStateUpdate request")
                Mono.just(true)
            }
        }
    }

    /**
     *
     * The method implements the functionality to update list item attributes.
     *
     */
    fun processUpdateListItem(
        guestId: String,
        listId: UUID,
        listItemId: UUID,
        listItemUpdateRequest: ListItemUpdateRequestTO
    ): Mono<CartItemResponse> {
        return getCartItem(listId, listItemId, listItemUpdateRequest)
                .flatMap {
                    val updateRequest = listItemUpdateRequest
                    val cartContent = it.first
                    val cartUUID = it.second
                    val existing = getUserItemMetaDataFromCart(cartContent.metadata) // Get user metaData
                    if (existing != null && listItemUpdateRequest.userItemMetaDataTransformationStep != null) {
                        listItemUpdateRequest.userItemMetaDataTransformationStep.execute(existing).map {
                            val transformedRequest = listItemUpdateRequest.copy(metadata = it.userMetaData)
                            Triple(cartContent, cartUUID, transformedRequest)
                        }
                    } else Mono.just(Triple(cartContent, cartUUID, updateRequest))
                }
                .flatMap {
                    updateCartItemsManager.updateCartItem(guestId = guestId, cartId = listId, cartItemId = listItemId,
                            cartItemUpdateRequest = toCartItemUpdateRequest(it.first, it.second, listItemId, it.third))
                }
    }

    /**
     *
     * The method implements the functionality to get the cartItem and also the listId in which the items was found.
     * If item is not found in either pending or the completed list exception is thrown.
     *
     */
    private fun getCartItem(
        listId: UUID,
        listItemId: UUID,
        listItemUpdateRequest: ListItemUpdateRequestTO
    ): Mono<Pair<CartItemResponse, UUID>> {
        return cartManager.getCartItem(cartItemId = listItemId, cartId = listId)
                .map {
                    if (it.tcin != null && listItemUpdateRequest.itemTitle != null)
                        throw BadRequestException(AppErrorCodes.ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("user should not set item_title for tcin item")))
                    Pair(it, listId) }
                .switchIfEmpty {
                    if (isTwoCartsEnabled) {
                        logger.error("Item not found in pending list, searching in the completed list")
                        cartManager.getCompletedListCart(listId)
                                .switchIfEmpty { throw RuntimeException("Completed cart not found") }
                                .flatMap { completedCart ->
                                    cartManager.getCartItem(cartId = completedCart.cartId!!, cartItemId = listItemId)
                                            .map {
                                                if (it.tcin != null && listItemUpdateRequest.itemTitle != null)
                                                    throw BadRequestException(AppErrorCodes.ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("user should not set item_title for tcin item")))
                                                Pair(it, completedCart.cartId!!) }
                                            .switchIfEmpty { throw RuntimeException("Cart Item $listItemId of list" +
                                                    " $listId not found") }
                                }
                    } else {
                        throw RuntimeException("Cart Item $listItemId of list $listId not found with feature two-cart=false")
                    }
                }
    }
}
