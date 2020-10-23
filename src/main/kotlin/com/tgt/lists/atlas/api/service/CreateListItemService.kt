package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.domain.AddListItemsManager
import com.tgt.lists.atlas.api.transport.ListItemRequestTO
import com.tgt.lists.atlas.api.transport.ListItemResponseTO
import com.tgt.lists.atlas.api.transport.mapper.ListItemMapper.Companion.toListItemResponseTO
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreateListItemService(@Inject private val addListItemsManager: AddListItemsManager) {
    private val logger = KotlinLogging.logger {}

    fun createListItem(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        listId: UUID,
        locationId: Long,
        items: ListItemRequestTO
    ): Mono<ListItemResponseTO> {
        logger.debug("[createListItems] guestId: $guestId, listId: $listId, locationId: $locationId")
        return addListItemsManager.addListItems(guestId, listId, LIST_ITEM_STATE.PENDING, listOf(items))
                .map { toListItemResponseTO(it.first()) }
    }
}
