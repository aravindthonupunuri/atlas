package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.EditListSortOrderRequestTO
import com.tgt.lists.atlas.api.util.AppErrorCodes
import com.tgt.lists.common.components.exception.BadRequestException
import io.micronaut.context.annotation.Value
import mu.KotlinLogging
import reactor.core.publisher.Mono
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EditListSortOrderService(
    @Value("\${list.list-type}") private val listType: String,
    @Inject private val listRepository: ListRepository,
    @Inject private val listSortOrderService: ListSortOrderService
) {
    private val logger = KotlinLogging.logger {}

    fun editListPosition(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        editListSortOrderRequestTO: EditListSortOrderRequestTO
    ): Mono<Boolean> {

        logger.debug("[editListPosition] guestId: $guestId, primaryListId: ${editListSortOrderRequestTO.primaryListId}, secondaryListId: ${editListSortOrderRequestTO.secondaryListId}")

        return listRepository.findListById(editListSortOrderRequestTO.primaryListId)
                .flatMap {
                    listRepository.findGuestListByGuestId(it.guestId!!, listType)
                            .collectList()
                            .map {
                                val isAuthorisedPrimaryList = it.find { it.id == editListSortOrderRequestTO.primaryListId }
                                val isAuthorisedSecondaryList = it.find { it.id == editListSortOrderRequestTO.secondaryListId }
                                if (isAuthorisedPrimaryList == null || isAuthorisedSecondaryList == null) {
                                    throw BadRequestException(AppErrorCodes.BAD_REQUEST_ERROR_CODE(listOf("user is not authorized to do this sort")))
                                }
                                editListSortOrderRequestTO
                            }
                            .flatMap {
                                if (it.primaryListId == it.secondaryListId) Mono.just(true)
                                else listSortOrderService.editListSortOrder(guestId, editListSortOrderRequestTO)
                            }
                }
    }
}
