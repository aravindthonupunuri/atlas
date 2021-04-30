package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.EditItemSortOrderRequestTO
import com.tgt.lists.atlas.api.util.ErrorCodes.LIST_ITEM_NOT_FOUND_ERROR_CODE
import com.tgt.lists.common.components.exception.BadRequestException
import com.tgt.lists.common.components.exception.ErrorCode
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
                        throw BadRequestException(ErrorCode(LIST_ITEM_NOT_FOUND_ERROR_CODE.first, LIST_ITEM_NOT_FOUND_ERROR_CODE.second, listOf("User is not authorized to do this sort [primary: ${editItemSortOrderRequestTO.primaryItemId}, secondary: ${editItemSortOrderRequestTO.secondaryItemId}]")))
                    }
                    editItemSortOrderRequestTO
                }
                .flatMap {
                    if (it.primaryItemId == it.secondaryItemId) Mono.just(true)
                    else listItemSortOrderService.editListItemSortOrder(listId, editItemSortOrderRequestTO)
                }
    }
}
