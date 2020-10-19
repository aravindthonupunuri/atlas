package com.tgt.lists.atlas.api.domain

import com.tgt.lists.common.components.exception.InternalServerException
import com.tgt.lists.atlas.api.domain.model.GuestPreference
import com.tgt.lists.atlas.api.persistence.GuestPreferenceRepository
import com.tgt.lists.atlas.api.util.AppErrorCodes
import com.tgt.lists.atlas.api.util.Direction
import io.micronaut.context.annotation.Requires
import mu.KotlinLogging
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Requires(property = "list.features.sort-position", value = "true")
class GuestPreferenceSortOrderManager(@Inject private val guestPreferenceRepository: GuestPreferenceRepository) {

    private val log = KotlinLogging.logger { GuestPreferenceSortOrderManager::class.java.name }

    fun saveNewOrder(guestId: String, listId: UUID): Mono<GuestPreference> {
        return guestPreferenceRepository.find(guestId)
            .flatMap {
                val dbGuestPreference = it
                val sortOrder = dbGuestPreference.listSortOrder
                sortOrder.split(",").toSet().takeIf { !it.contains(listId.toString()) }
                    ?.let {
                        val newSortOrder = addListIdToSortOrder(listId, sortOrder, 0)
                        guestPreferenceRepository.update(guestId, newSortOrder).map { dbGuestPreference.copy(listSortOrder = newSortOrder) }
                    } ?: Mono.just(it)
            }.switchIfEmpty { guestPreferenceRepository.save(GuestPreference(guestId, listId.toString())) }
    }

    fun updateSortOrder(guestId: String, primaryListId: UUID, secondaryListId: UUID, direction: Direction): Mono<GuestPreference> {
        if (primaryListId == secondaryListId) {
            return Mono.just(GuestPreference(guestId, ""))
        }

        return guestPreferenceRepository.find(guestId)
            .flatMap {
                val newSortOrder = editListIdPosSortOrder(primaryListId, secondaryListId, direction, it.listSortOrder)
                if (newSortOrder == it.listSortOrder) {
                    Mono.just(it)
                } else {
                    guestPreferenceRepository.update(guestId, newSortOrder).map { GuestPreference(guestId, newSortOrder) }
                }
            }
                .switchIfEmpty {
                    log.error("Unable to find guest $guestId in the repository")
                    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                    Mono.just<GuestPreference>(null)
                }
    }

    fun removeListIdFromSortOrder(guestId: String, listId: UUID): Mono<GuestPreference> {
        return guestPreferenceRepository.find(guestId)
            .flatMap {
                val sortOrders = it.listSortOrder.split(",").toMutableSet()
                if (!sortOrders.contains(listId.toString())) {
                    throw InternalServerException(AppErrorCodes.LIST_SORT_ORDER_ERROR_CODE(listOf("The primary list id to move is not present $listId"))) // this is not a bad request b'cose of order of events
                } else {
                    val newSortOrder = removeListIdFromSortOrder(listId, it.listSortOrder)
                    guestPreferenceRepository.update(guestId, newSortOrder).map { GuestPreference(guestId, newSortOrder) } }
                }
            .switchIfEmpty { Mono.just(GuestPreference(guestId, "")) }
    }

    fun getGuestPreference(guestId: String): Mono<GuestPreference> {
        return guestPreferenceRepository.find(guestId)
            .switchIfEmpty { Mono.just(GuestPreference(guestId, "")) }
            .onErrorResume {
                log.error("Exception while getting sort order: ", it)
                Mono.just(GuestPreference(guestId, ""))
            }
    }

    private fun addListIdToSortOrder(listId: UUID, sortOrder: String, position: Int): String {
        val list = sortOrder.split(",").toMutableList()
        list.add(position, listId.toString())
        val newSortOrder = list.joinToString(",")
        return if (newSortOrder.endsWith(",")) newSortOrder.substring(0, newSortOrder.lastIndex) else newSortOrder
    }

    private fun editListIdPosSortOrder(
        primaryListId: UUID,
        secondaryListId: UUID,
        direction: Direction,
        sortOrder: String
    ): String {
        val list = sortOrder.split(",").toMutableList()
        if (!list.contains(secondaryListId.toString())) {
            list.add(0, secondaryListId.toString())
        }
        list.remove(primaryListId.toString())
        val newPosition = if (direction == Direction.ABOVE) list.indexOf(secondaryListId.toString()) else list.indexOf(secondaryListId.toString()) + 1
        list.add(newPosition, primaryListId.toString())
        val newSortOrder = list.joinToString(",")
        return if (newSortOrder.endsWith(",")) newSortOrder.substring(0, newSortOrder.lastIndex) else newSortOrder
    }

    private fun removeListIdFromSortOrder(listId: UUID, sortOrder: String): String {
        val list = sortOrder.split(",").toMutableSet()
        list.remove(listId.toString())
        val newSortOrder = list.joinToString(",")
        return if (newSortOrder.endsWith(",")) newSortOrder.substring(0, newSortOrder.lastIndex) else newSortOrder
    }
}
