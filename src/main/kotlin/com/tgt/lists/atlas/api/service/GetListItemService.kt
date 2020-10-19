package com.tgt.lists.atlas.api.service

import com.tgt.lists.common.components.exception.ResourceNotFoundException
import com.tgt.lists.atlas.api.domain.CartManager
import com.tgt.lists.atlas.api.transport.ListItemResponseTO
import com.tgt.lists.atlas.api.transport.toListItemResponseTO
import com.tgt.lists.atlas.api.util.AppErrorCodes
import com.tgt.lists.atlas.api.util.CartManagerName
import com.tgt.lists.atlas.api.util.GuestId
import io.micronaut.context.annotation.Value
import mu.KotlinLogging
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetListItemService(
    @CartManagerName("GetListItemService") @Inject private val cartManager: CartManager,
    @Value("\${list.features.two-carts}") private val isTwoCartsEnabled: Boolean
) {

    private val logger = KotlinLogging.logger {}

    fun getListItem(
        guestId: GuestId, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        locationId: Long,
        listId: UUID,
        listItemId: UUID
    ): Mono<ListItemResponseTO> {

        logger.debug("[getListItem] guestId: $guestId, listId: $listId, listItemId: $listItemId, locationId: $locationId")

        return cartManager.getCartItem(cartItemId = listItemId, cartId = listId)
                .switchIfEmpty {
                    logger.error { "Item is not found in pending list trying to fetch from completed list" }
                    if (!isTwoCartsEnabled) {
                        throw ResourceNotFoundException(AppErrorCodes.RESOURCE_NOT_FOUND_ERROR_CODE(listOf("ListItemId: $listItemId not found in List: $listId")))
                    }
                    cartManager.getItemInCompletedCart(listId, listItemId)
                            .switchIfEmpty {
                                logger.error { "Item  not found in completed list" }
                                throw ResourceNotFoundException(AppErrorCodes.RESOURCE_NOT_FOUND_ERROR_CODE(listOf("ListItemId: $listItemId not found with List: $listId")))
                            }
                }
                .map { toListItemResponseTO(it) }
    }
}
