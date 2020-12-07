package com.tgt.lists.atlas.api.domain

import com.tgt.lists.atlas.api.domain.model.entity.GuestPreferenceEntity
import com.tgt.lists.atlas.api.persistence.cassandra.GuestPreferenceRepository
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.ListGetAllResponseTO
import com.tgt.lists.atlas.api.transport.mapper.ListMapper
import com.tgt.lists.atlas.api.type.Direction
import com.tgt.lists.atlas.api.util.ErrorCodes.LIST_SORT_ORDER_ERROR_CODE
import com.tgt.lists.common.components.exception.ErrorCode
import com.tgt.lists.common.components.exception.InternalServerException
import mu.KotlinLogging
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GuestPreferenceSortOrderManager(
    @Inject private val guestPreferenceRepository: GuestPreferenceRepository,
    @Inject private val listRepository: ListRepository
) {

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

    fun updateSortOrder(guestId: String, listType: String, primaryListId: UUID, secondaryListId: UUID, direction: Direction): Mono<GuestPreferenceEntity> {
        if (primaryListId == secondaryListId) {
            return Mono.just(GuestPreferenceEntity(guestId = guestId, listSortOrder = ""))
        }

        return listRepository.findGuestLists(guestId, listType).map { it.map { ListMapper.toListGetAllResponseTO(it, 0) } }
                .zipWith(
                        guestPreferenceRepository.findGuestPreference(guestId).map { it.listSortOrder!! }.switchIfEmpty { Mono.just("") }
                )
                .map {
                    // sort listOfLists using db sort order, and then form current sort order string based on this sorted listOfLists
                    val lists = it.t1
                    val dbSortOrder: String = it.t2
                    if (dbSortOrder.isNotBlank()) {
                        sortListOfLists(dbSortOrder, lists).map { it.listId }.joinToString(",")
                    } else {
                        // no dbSortOrder available, use natural order as current sort order
                        lists.map { it.listId }.joinToString(",")
                    }
                }
                .flatMap {
                    // apply guest initiated order change and form new sort order to save in db
                    val currentListSortOrder = it
                    val newSortOrder = editListIdPosSortOrder(primaryListId, secondaryListId, direction, currentListSortOrder)
                    guestPreferenceRepository.saveGuestPreference(GuestPreferenceEntity(guestId = guestId, listSortOrder = newSortOrder))
                }
    }

    fun removeListIdFromSortOrder(guestId: String, listId: UUID): Mono<GuestPreferenceEntity> {
        return guestPreferenceRepository.findGuestPreference(guestId)
            .flatMap {
                val sortOrders = it.listSortOrder?.split(",")?.toMutableSet()
                if (!sortOrders!!.contains(listId.toString())) {
                    throw InternalServerException(ErrorCode(LIST_SORT_ORDER_ERROR_CODE.first, LIST_SORT_ORDER_ERROR_CODE.second, listOf("The primary list id to move is not present $listId"))) // this is not a bad request b'cose of order of events
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

    fun sortListOfLists(sortOrder: String, guestLists: List<ListGetAllResponseTO>): List<ListGetAllResponseTO> {
        val listSortOrderMap = sortOrder.split(",").mapIndexed {
            index, s -> s to index.toLong()
        }.toMap()

        val wrappedList = guestLists.map {
            var position = listSortOrderMap[it.listId.toString()]
            if (position == null) {
                // we are dealing with timeUUID here which can be converted to timestamp
                position = it.listId!!.timestamp()
            }
            ListGetAllResponseTOWrapper(position, it)
        }

        return wrappedList.sortedWith(compareBy { it.listPosition }).map { it.listGetAllResponseTO }
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

    /**
     * Used to assist in sorting via standard comparator
     */
    data class ListGetAllResponseTOWrapper(
        val listPosition: Long,
        val listGetAllResponseTO: ListGetAllResponseTO
    )
}
