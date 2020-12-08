package com.tgt.lists.atlas.api.domain

import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.type.LIST_MARKER
import com.tgt.lists.atlas.api.type.LIST_STATE
import com.tgt.lists.atlas.api.util.ErrorCodes.MAX_LISTS_COUNT_VIOLATION_ERROR_CODE
import com.tgt.lists.atlas.api.util.ErrorCodes.UPDATE_DEFAULT_LIST_VIOLATION_ERROR_CODE
import com.tgt.lists.common.components.exception.BadRequestException
import com.tgt.lists.common.components.exception.BaseErrorCodes.FORBIDDEN_ERROR_CODE
import com.tgt.lists.common.components.exception.ErrorCode
import com.tgt.lists.common.components.exception.ForbiddenException
import mu.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultListManager(
    @Inject private val listRepository: ListRepository,
    @Inject private val updateListManager: UpdateListManager,
    @Inject val configuration: Configuration
) {

    private val logger = KotlinLogging.logger { DefaultListManager::class.java.name }

    private val fixedDefaultList = configuration.fixedDefaultList
    private val listType = configuration.listType
    private val maxListsCount = configuration.maxListsCount

    //  1. Check if the new list being created exceeds the max allowed lists for a user
    //  2. Update defaultListIndicator
    fun processDefaultListInd(guestId: String, defaultListIndicator: Boolean, listId: UUID? = null): Mono<Boolean> {
        return listRepository.findGuestLists(guestId, listType).flatMap {
            if (it.isNullOrEmpty()) {
                logger.debug("[processDefaultListInd] No lists found for guest with guestId: $guestId and listType: $listType")
                Mono.just(true) // No preexisting lists found
            } else {
                // check if list exceeds max lists allowed per user
                checkGuestListsCount(it, listId)
                // update defaultListIndicator
                setDefaultList(it, guestId, defaultListIndicator, listId)
            }
        }
    }

    fun setDefaultList(
        guestLists: List<ListEntity>,
        guestId: String,
        defaultListIndicator: Boolean,
        listId: UUID?
    ): Mono<Boolean> {
        return if (defaultListIndicator) {
            // This method can be called either during create new list (listId=null) or during update of an existing list (listId NOT null).
            // Create list is always called with guestId as ownerId for new list.
            // Update can be called with guestId as operation executor who could be different than list owner.
            // So we have a check to block default list indicator processing if its not the owner of the list.
            if (listId != null && guestLists.firstOrNull { it.id == listId } == null) {
                throw ForbiddenException(ErrorCode(FORBIDDEN_ERROR_CODE, listOf("guestId not authorized to update default list indicator, guestId: $guestId is not the owner of the list")))
            }
            if (fixedDefaultList) {
                throw BadRequestException(ErrorCode(UPDATE_DEFAULT_LIST_VIOLATION_ERROR_CODE.first, UPDATE_DEFAULT_LIST_VIOLATION_ERROR_CODE.second))
            }
            Flux.fromIterable(getDefaultLists(guestLists, listId).asIterable()).flatMap { guestList ->
                updateListManager.updateList(guestId = guestId, listId = guestList.id!!,
                        updatedListEntity = guestList.copy(marker = ""), existingListEntity = guestList)
            }.then(Mono.just(true))
        } else {
            Mono.just(false)
        }
    }

    fun checkGuestListsCount(guestLists: List<ListEntity>, listId: UUID?) {
        // Skipping the check if listId is null as it implies its from update list
        if (listId == null && guestLists.filter { it.state == LIST_STATE.ACTIVE.value }.count() >= maxListsCount) {
            throw BadRequestException(ErrorCode(MAX_LISTS_COUNT_VIOLATION_ERROR_CODE.first, MAX_LISTS_COUNT_VIOLATION_ERROR_CODE.second))
        }
    }

    fun getDefaultLists(
        guestLists: List<ListEntity>,
        listId: UUID?
    ): List<ListEntity> {
        // Skip the list that you are trying to process, since its already a default list.
        return guestLists.filter {
            (it.state == LIST_STATE.ACTIVE.value) &&
                    (it.marker == LIST_MARKER.DEFAULT.value) &&
                    (listId == null || it.id != listId)
        }.toList()
    }
}
