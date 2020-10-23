package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.domain.DeleteCartItemsManager
import com.tgt.lists.atlas.api.transport.ListItemDeleteResponseTO
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeleteListItemService(
    @Inject private val deleteCartItemsManager: DeleteCartItemsManager
) {
    private val logger = KotlinLogging.logger {}

    fun deleteListItem(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        listId: UUID,
        listItemId: UUID
    ): Mono<ListItemDeleteResponseTO> {

        logger.debug("[DeleteListItemService] guestId: $guestId, listId: $listId, listItemId: $listItemId")

        return deleteCartItemsManager.deleteCartItem(guestId, listId, listItemId)
            .map { ListItemDeleteResponseTO(listId = listId, listItemId = it.cartItemId) }
    }
}
