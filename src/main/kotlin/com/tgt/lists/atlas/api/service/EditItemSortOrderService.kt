package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.EditItemSortOrderRequestTO
import com.tgt.lists.atlas.api.util.AppErrorCodes
import com.tgt.lists.common.components.exception.BadRequestException
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EditItemSortOrderService(
    @Inject private val listRepository: ListRepository,
    @Inject private val listItemSortOrderService: ListItemSortOrderService
) {
    private val logger = KotlinLogging.logger {}

    fun editItemPosition(listId: UUID, editItemSortOrderRequestTO: EditItemSortOrderRequestTO): Mono<Boolean> {

        logger.debug("[editItemPosition] listId: $listId, primaryItemId: ${editItemSortOrderRequestTO.primaryItemId}, secondaryItemId: ${editItemSortOrderRequestTO.secondaryItemId}")

        return listRepository.findListItemsByListId(listId)
                .collectList()
                .map {
                    val isAuthorisedPrimaryItem = it.find { it.itemId == editItemSortOrderRequestTO.primaryItemId }
                    val isAuthorisedSecondaryItem = it.find { it.itemId == editItemSortOrderRequestTO.secondaryItemId }
                    if (isAuthorisedPrimaryItem == null || isAuthorisedSecondaryItem == null) {
                        throw BadRequestException(AppErrorCodes.BAD_REQUEST_ERROR_CODE(listOf("user is not authorized to do this sort")))
                    }
                    editItemSortOrderRequestTO
                }
                .flatMap {
                    if (it.primaryItemId == it.secondaryItemId) Mono.just(true)
                    else listItemSortOrderService.editListItemSortOrder(listId, editItemSortOrderRequestTO)
                }
    }
}
