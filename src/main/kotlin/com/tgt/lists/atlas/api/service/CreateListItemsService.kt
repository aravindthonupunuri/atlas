package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.domain.CreateListItemsManager
import com.tgt.lists.atlas.api.transport.ListItemRequestTO
import com.tgt.lists.atlas.api.transport.ListItemsResponseTO
import com.tgt.lists.atlas.api.transport.mapper.ListItemMapper.Companion.toListItemResponseTO
import com.tgt.lists.atlas.api.type.LIST_ITEM_STATE
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreateListItemsService(@Inject private val createListItemsManager: CreateListItemsManager) {
    private val logger = KotlinLogging.logger {}

    fun createListItems(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        listId: UUID,
        locationId: Long,
        items: List<ListItemRequestTO>
    ): Mono<ListItemsResponseTO> {

        logger.debug("[addMultipleListItem] guestId: $guestId, listId: $listId, locationId: $locationId")

        return createListItemsManager.createListItems(guestId, listId, LIST_ITEM_STATE.PENDING, items)
                .map { listItems -> ListItemsResponseTO(listId, listItems.map { toListItemResponseTO(it) }) }
    }
}
