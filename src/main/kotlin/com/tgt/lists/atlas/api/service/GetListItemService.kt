package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.ListItemResponseTO
import com.tgt.lists.atlas.api.transport.mapper.ListItemMapper.Companion.toListItemResponseTO
import com.tgt.lists.atlas.api.type.GuestId
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetListItemService(@Inject private val listRepository: ListRepository) {

    private val logger = KotlinLogging.logger {}

    fun getListItem(
        guestId: GuestId, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        locationId: Long,
        listId: UUID,
        listItemId: UUID
    ): Mono<ListItemResponseTO> {

        logger.debug("[getListItem] guestId: $guestId, listId: $listId, listItemId: $listItemId, locationId: $locationId")

        return listRepository.findListItemByItemId(listId, listItemId).map { toListItemResponseTO(it) }
    }
}
