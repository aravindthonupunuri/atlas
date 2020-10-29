package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.domain.GuestPreferenceSortOrderManager
import com.tgt.lists.atlas.api.domain.ListItemSortOrderManager
import com.tgt.lists.atlas.api.transport.EditListSortOrderRequestTO
import com.tgt.lists.atlas.api.util.LIST_STATUS
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListSortOrderService(
    @Inject private val guestPreferenceSortOrderManager: GuestPreferenceSortOrderManager,
    @Inject private val listItemSortOrderManager: ListItemSortOrderManager
) {

    private val logger = KotlinLogging.logger { ListSortOrderService::class.java.name }

    fun saveListSortOrder(
        guestId: String, // list owner guest id
        listId: UUID,
        listStatus: LIST_STATUS
    ): Mono<Boolean> {

        logger.debug("[saveListSortOrder] guestId: $guestId, listId: $listId")

        return if (listStatus == LIST_STATUS.PENDING) {
            guestPreferenceSortOrderManager.saveNewOrder(guestId, listId)
                    .map { true }
                    .onErrorResume {
                        logger.error("Exception while saving list sort order", it)
                        Mono.just(false)
                    }
        } else { Mono.just(true) }
    }

    fun deleteListSortOrder(
        guestId: String,
        listId: UUID,
        listStatus: LIST_STATUS
    ): Mono<Boolean> {

        logger.debug("[deleteListSortOrder] guestId: $guestId, listId: $listId")

        return if (listStatus == LIST_STATUS.PENDING) {
            listItemSortOrderManager.deleteById(guestId, listId)
                    .flatMap { guestPreferenceSortOrderManager.removeListIdFromSortOrder(guestId, listId) }
                    .map { true }
                    .onErrorResume {
                        logger.error("Exception while deleting list sort order", it)
                        Mono.just(false)
                    }
        } else { Mono.just(true) }
    }

    fun editListSortOrder(
        guestId: String,
        editListSortOrderRequestTO: EditListSortOrderRequestTO
    ): Mono<Boolean> {

        logger.debug("[editListSortOrder] editListSortOrderRequestTO: $editListSortOrderRequestTO")

        return guestPreferenceSortOrderManager.updateSortOrder(guestId, editListSortOrderRequestTO.primaryListId,
                editListSortOrderRequestTO.secondaryListId, editListSortOrderRequestTO.direction).map { true }
                .onErrorResume {
                    logger.error("Exception while editing list sort order", it)
                    Mono.just(false)
                }
    }
}
