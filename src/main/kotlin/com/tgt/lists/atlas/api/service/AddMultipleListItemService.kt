package com.tgt.lists.atlas.api.service

import com.tgt.lists.cart.transport.CartItemResponse
import com.tgt.lists.atlas.api.domain.AddMultiItemsManager
import com.tgt.lists.atlas.api.transport.ListItemMultiAddResponseTO
import com.tgt.lists.atlas.api.transport.ListItemRequestTO
import com.tgt.lists.atlas.api.transport.toListItemResponseTO
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddMultipleListItemService(@Inject private val addMultiItemsManager: AddMultiItemsManager) {
    private val logger = KotlinLogging.logger {}

    fun addMultipleListItem(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        listId: UUID,
        locationId: Long,
        items: List<ListItemRequestTO>
    ): Mono<ListItemMultiAddResponseTO> {

        logger.debug("[addMultipleListItem] guestId: $guestId, listId: $listId, locationId: $locationId")

        return addMultiItemsManager.processAddMultiItems(guestId, locationId, listId, listId,
                LIST_ITEM_STATE.PENDING, items)
                .flatMap { toListItemMultiAddResponseTO(it, listId) }
    }

    private fun toListItemMultiAddResponseTO(
        cartItemResponseList: List<CartItemResponse>,
        listId: UUID
    ): Mono<ListItemMultiAddResponseTO> {
        return Mono.just(ListItemMultiAddResponseTO(listId, cartItemResponseList.map { toListItemResponseTO(it) }))
    }
}
