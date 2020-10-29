package com.tgt.lists.atlas.api.domain

import com.tgt.lists.atlas.api.domain.model.entity.GuestPreferenceEntity
import com.tgt.lists.atlas.api.persistence.cassandra.GuestPreferenceRepository
import com.tgt.lists.atlas.api.util.AppErrorCodes
import com.tgt.lists.atlas.api.util.Direction
import com.tgt.lists.common.components.exception.InternalServerException
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

    fun saveNewOrder(guestId: String, listId: UUID): Mono<GuestPreferenceEntity> {
        return guestPreferenceRepository.findGuestPreference(guestId)
            .flatMap {
                val dbGuestPreference = it
                val sortOrder = dbGuestPreference.listSortOrder
                sortOrder?.split(",")?.toSet().takeIf { !it!!.contains(listId.toString()) }
                    ?.let {
                        val newSortOrder = addListIdToSortOrder(listId, sortOrder!!, 0)
                        guestPreferenceRepository.saveGuestPreference(dbGuestPreference.copy(listSortOrder = newSortOrder))
                    } ?: Mono.just(it)
            }.switchIfEmpty { guestPreferenceRepository.saveGuestPreference(GuestPreferenceEntity(guestId = guestId, listSortOrder = listId.toString())) }
    }

    fun updateSortOrder(guestId: String, primaryListId: UUID, secondaryListId: UUID, direction: Direction): Mono<GuestPreferenceEntity> {
        if (primaryListId == secondaryListId) {
            return Mono.just(GuestPreferenceEntity(guestId = guestId, listSortOrder = ""))
        }

        return guestPreferenceRepository.findGuestPreference(guestId)
            .flatMap {
                val newSortOrder = it.listSortOrder?.let { it1 -> editListIdPosSortOrder(primaryListId, secondaryListId, direction, it1) }
                if (newSortOrder == it.listSortOrder) {
                    Mono.just(it)
                } else { guestPreferenceRepository.saveGuestPreference(GuestPreferenceEntity(guestId = guestId, listSortOrder = newSortOrder!!)) }
            }
                .switchIfEmpty {
                    log.error("Unable to find guest $guestId in the repository")
                    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                    Mono.just<GuestPreferenceEntity>(null)
                }
    }

    fun removeListIdFromSortOrder(guestId: String, listId: UUID): Mono<GuestPreferenceEntity> {
        return guestPreferenceRepository.findGuestPreference(guestId)
            .flatMap {
                val sortOrders = it.listSortOrder?.split(",")?.toMutableSet()
                if (!sortOrders!!.contains(listId.toString())) {
                    throw InternalServerException(AppErrorCodes.LIST_SORT_ORDER_ERROR_CODE(listOf("The primary list id to move is not present $listId"))) // this is not a bad request b'cose of order of events
                } else {
                    val newSortOrder = removeListIdFromSortOrder(listId, it.listSortOrder!!)
                    guestPreferenceRepository.saveGuestPreference(GuestPreferenceEntity(guestId = guestId, listSortOrder = newSortOrder))
                }
            }.switchIfEmpty { Mono.just(GuestPreferenceEntity(guestId = guestId, listSortOrder = "")) }
    }

    fun getGuestPreference(guestId: String): Mono<GuestPreferenceEntity> {
        return guestPreferenceRepository.findGuestPreference(guestId)
            .switchIfEmpty { Mono.just(GuestPreferenceEntity(guestId = guestId, listSortOrder = "")) }
            .onErrorResume {
                log.error("Exception while getting sort order: ", it)
                Mono.just(GuestPreferenceEntity(guestId = guestId, listSortOrder = ""))
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
