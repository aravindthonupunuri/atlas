package com.tgt.lists.atlas.api.domain

import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.util.AppErrorCodes
import com.tgt.lists.atlas.api.util.CartManagerName
import com.tgt.lists.atlas.api.util.LIST_MARKER
import com.tgt.lists.atlas.api.util.LIST_STATE
import com.tgt.lists.common.components.exception.BadRequestException
import com.tgt.lists.common.components.exception.BaseErrorCodes
import com.tgt.lists.common.components.exception.ForbiddenException
import io.micronaut.context.annotation.Value
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultListManager(
    @CartManagerName("DefaultListManager") @Inject private val cartManager: CartManager,
    @Inject private val listRepository: ListRepository,
    @Inject private val updateListManager: UpdateListManager,
    @Value("\${list.max-count}") private val maxListsCount: Int = 50,
    @Value("\${list.list-type}") private val listType: String,
    @Value("\${list.features.fixed-default-list}") private val isFixedDefaultListEnabled: Boolean
) {
    fun processDefaultListInd(guestId: String, defaultListIndicator: Boolean, listId: UUID? = null): Mono<Boolean> {
        return findGuestLists(guestId, listType).flatMap {
            // this method can be called either during create new list (listId=null)or during update if an existing list (listId NOT null)
            // create list is always called with the guestId as ownerId for new list
            // whereas update may be called with guestId as operation executor who could be different than list owner
            // hence we make the check to block default list indicator processing if its not the owner of the list
            if (listId != null && it.firstOrNull { it.id == listId } == null) {
                throw ForbiddenException(BaseErrorCodes.FORBIDDEN_ERROR_CODE(listOf("guestId not authorized to update" +
                        " default list indicator, guestId: $guestId is not the owner of the list")))
            }
            checkGuestListsCount(it, listId)
            setDefaultList(it, guestId, defaultListIndicator, listId)
        }
    }

    fun checkGuestListsCount(guestLists: List<ListEntity>, listId: UUID?) {
        // Skipping the check if listId is null as it implies its from update list
        if (listId == null && guestLists.filter { it.state == LIST_STATE.ACTIVE.value }.count() >= maxListsCount) {
            throw BadRequestException(AppErrorCodes.LIST_MAX_COUNT_VIOLATION_ERROR_CODE(arrayListOf("Max guests lists reached")))
        }
    }

    fun setDefaultList(
        guestLists: List<ListEntity>,
        guestId: String,
        defaultListIndicator: Boolean,
        listId: UUID?
    ): Mono<Boolean> {
        val defaultLists = getDefaultLists(guestLists, listId)
        if (defaultLists.isEmpty()) {
            return Mono.just(true) // No preexisting carts with default list found
        }
        if (isFixedDefaultListEnabled) {
            return Mono.just(false)
        }

        return if (defaultListIndicator) {
            Flux.fromIterable(defaultLists.asIterable()).flatMap { guestList ->
                updateListManager.updateList(guestId = guestId, listId = guestList.id!!,
                        updatedListEntity = guestList.copy(marker = LIST_MARKER.DEFAULT.value), existingListEntity = guestList)
            }.then(Mono.just(defaultListIndicator))
        } else {
            Mono.just(defaultListIndicator)
        }
    }

    private fun findGuestLists(
        guestId: String,
        listType: String
    ): Mono<List<ListEntity>> {
        return listRepository.findGuestListsByGuestId(guestId, listType).collectList().flatMap {
            if (it.isNullOrEmpty()) {
                Mono.just(emptyList())
            } else {
                listRepository.findMultipleListsById(it.map { it.id!! }.toList()).collectList()
            }
        }
    }

    fun getDefaultLists(
        guestLists: List<ListEntity>,
        listId: UUID?
    ): List<ListEntity> {
        return guestLists
            .filter {
                (it.state == LIST_STATE.ACTIVE.value) &&
                        (it.marker == LIST_MARKER.DEFAULT.value) &&
                        (listId == null || it.id != listId) // Skip the list that you are trying to process, since its already a default list..
            }
    }
}
