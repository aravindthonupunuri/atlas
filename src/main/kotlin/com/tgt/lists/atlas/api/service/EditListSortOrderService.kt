package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.domain.Configuration
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.EditListSortOrderRequestTO
import com.tgt.lists.atlas.api.util.ErrorCodes.LIST_NOT_FOUND_ERROR_CODE
import com.tgt.lists.atlas.api.util.ErrorCodes.LIST_SORT_ORDER_ERROR_CODE
import com.tgt.lists.common.components.exception.BadRequestException
import com.tgt.lists.common.components.exception.ErrorCode
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EditListSortOrderService(
    @Inject private val listRepository: ListRepository,
    @Inject private val listSortOrderService: ListSortOrderService,
    @Inject private val configuration: Configuration

) {
    private val logger = KotlinLogging.logger {}

    private val listType: String = configuration.listType

    fun editListPosition(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        authorizedListId: UUID,
        editListSortOrderRequestTO: EditListSortOrderRequestTO
    ): Mono<Boolean> {
        if (authorizedListId == editListSortOrderRequestTO.primaryListId || authorizedListId == editListSortOrderRequestTO.secondaryListId) {
            logger.debug("[editListPosition] guestId: $guestId, primaryListId: ${editListSortOrderRequestTO.primaryListId}, secondaryListId: ${editListSortOrderRequestTO.secondaryListId}")

            return listRepository.findListById(editListSortOrderRequestTO.primaryListId)
                    .flatMap {
                        listRepository.findGuestListsByGuestId(it.guestId!!, listType)
                                .collectList()
                                .map {
                                    val isAuthorisedPrimaryList = it.find { it.id == editListSortOrderRequestTO.primaryListId }
                                    val isAuthorisedSecondaryList = it.find { it.id == editListSortOrderRequestTO.secondaryListId }
                                    if (isAuthorisedPrimaryList == null || isAuthorisedSecondaryList == null) {
                                        throw BadRequestException(ErrorCode(LIST_NOT_FOUND_ERROR_CODE.first, LIST_NOT_FOUND_ERROR_CODE.second, listOf("User is not authorized to do this sort for lists [primary: ${editListSortOrderRequestTO.primaryListId}, secondary: ${editListSortOrderRequestTO.secondaryListId}]")))
                                    }
                                    editListSortOrderRequestTO
                                }
                                .flatMap {
                                    if (it.primaryListId == it.secondaryListId) Mono.just(true)
                                    else listSortOrderService.editListSortOrder(guestId, listType, editListSortOrderRequestTO)
                                }
                    }
        } else {
            throw BadRequestException(ErrorCode(LIST_SORT_ORDER_ERROR_CODE.first, LIST_SORT_ORDER_ERROR_CODE.second, arrayListOf("Authorized list Id [$authorizedListId] from Url does not match primary [${editListSortOrderRequestTO.primaryListId}] or secondary [${editListSortOrderRequestTO.secondaryListId}] listId")))
        }
    }
}
