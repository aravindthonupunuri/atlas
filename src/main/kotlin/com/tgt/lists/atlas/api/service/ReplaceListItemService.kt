package com.tgt.lists.atlas.api.service

import com.tgt.lists.common.components.exception.BadRequestException
import com.tgt.lists.atlas.api.transport.ListItemRequestTO
import com.tgt.lists.atlas.api.transport.ListItemResponseTO
import com.tgt.lists.atlas.api.util.AppErrorCodes
import com.tgt.lists.atlas.api.util.ItemType
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReplaceListItemService(
    @Inject private val createListItemService: CreateListItemService,
    @Inject private val deleteListItemService: DeleteListItemService
) {
    private val logger = KotlinLogging.logger {}

    fun replaceListItem(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        listId: UUID,
        sourceItemId: UUID,
        locationId: Long,
        listItemRequestTO: ListItemRequestTO
    ): Mono<ListItemResponseTO> {

        logger.debug("[replaceListItem] guestId: $guestId, listId: $listId, sourceItemId: $sourceItemId, locationId: $locationId")

        if (listItemRequestTO.itemType == ItemType.OFFER) {
            throw BadRequestException(AppErrorCodes.REQUEST_BODY_VIOLATION_ERROR_CODE(arrayListOf("item of type offer is not eligible to replace item")))
        }
        return createListItemService.createListItem(guestId, listId, locationId, listItemRequestTO)
            .flatMap { result -> deleteListItemService.deleteListItem(guestId, listId, sourceItemId).map { result } }
    }
}
