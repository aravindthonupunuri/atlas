package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.domain.GuestPreferenceSortOrderManager
import com.tgt.lists.atlas.api.domain.ListPreferenceSortOrderManager
import com.tgt.lists.atlas.api.transport.EditListSortOrderRequestTO
import com.tgt.lists.atlas.api.util.LIST_STATE
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListSortOrderService(
    @Inject private val guestPreferenceSortOrderManager: GuestPreferenceSortOrderManager,
    @Inject private val listPreferenceSortOrderManager: ListPreferenceSortOrderManager
) {

    private val logger = KotlinLogging.logger { ListSortOrderService::class.java.name }

    fun saveListSortOrder(
        guestId: String, // list owner guest id
        listId: UUID,
        listState: LIST_STATE
    ): Mono<Boolean> {

        logger.debug("[saveListSortOrder] guestId: $guestId, listId: $listId")

        return if (listState == LIST_STATE.ACTIVE) {
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
        listId: UUID
    ): Mono<Boolean> {

        logger.debug("[deleteListSortOrder] guestId: $guestId, listId: $listId")

        return listPreferenceSortOrderManager.deleteById(guestId, listId)
                .flatMap { guestPreferenceSortOrderManager.removeListIdFromSortOrder(guestId, listId) }
                .map { true }
                .onErrorResume {
                    logger.error("Exception while deleting list sort order", it)
                    Mono.just(false)
                }
    }

    fun editListSortOrder(
        guestId: String,
        listType: String,
        editListSortOrderRequestTO: EditListSortOrderRequestTO
    ): Mono<Boolean> {

        logger.debug("[editListSortOrder] editListSortOrderRequestTO: $editListSortOrderRequestTO")

        return guestPreferenceSortOrderManager.updateSortOrder(guestId, listType, editListSortOrderRequestTO.primaryListId,
                editListSortOrderRequestTO.secondaryListId, editListSortOrderRequestTO.direction).map { true }
                .onErrorResume {
                    logger.error("Exception while editing list sort order", it)
                    Mono.just(false)
                }
    }
}
