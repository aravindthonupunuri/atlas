package com.tgt.lists.atlas.api.domain

import com.tgt.lists.atlas.api.domain.model.entity.ListPreferenceEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListPreferenceRepository
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.ListItemResponseTO
import com.tgt.lists.atlas.api.transport.mapper.ListItemMapper
import com.tgt.lists.atlas.api.util.AppErrorCodes.LIST_ITEM_SORT_ORDER_ERROR_CODE
import com.tgt.lists.atlas.api.util.Direction
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.common.components.exception.InternalServerException
import mu.KotlinLogging
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListPreferenceSortOrderManager(
    @Inject private val listPreferenceRepository: ListPreferenceRepository,
    @Inject private val listRepository: ListRepository
) {

    private val logger = KotlinLogging.logger { ListPreferenceSortOrderManager::class.java.name }

    // guestid gives luxury to sort depending on their preference in case list is collaborated with multiple guests
    fun saveNewListItemOrder(guestId: String, listId: UUID, listItemId: UUID): Mono<ListPreferenceEntity> {
        return listPreferenceRepository.getListPreference(listId, guestId)
                .flatMap {
                    val dbList = it
                    val actualItemIdList = dbList.itemSortOrder?.split(",")?.toSet()
                    if (actualItemIdList != null && actualItemIdList.contains(listItemId.toString())) {
                        Mono.just(it)
                    } else {
                        val newSortOrder = addListItemIdToSortOrder(listItemId, it.itemSortOrder!!, 0)
                        listPreferenceRepository.saveListPreference(dbList.copy(itemSortOrder = newSortOrder))
                    }
                }.switchIfEmpty { listPreferenceRepository.saveListPreference(
                        ListPreferenceEntity(guestId = guestId, listId = listId, itemSortOrder = listItemId.toString())) }
    }

    fun updateListItemSortOrder(
        guestId: String,
        listId: UUID,
        primaryListItemId: UUID,
        secondaryListItemId: UUID,
        direction: Direction
    ): Mono<ListPreferenceEntity> {
        if (primaryListItemId == secondaryListItemId) {
            return Mono.just(ListPreferenceEntity(listId = listId, guestId = guestId, itemSortOrder = ""))
        }

        // sort order update in only applicable for pending items
        return listRepository.findListAndItemsByListIdAndItemState(listId, LIST_ITEM_STATE.PENDING.value).map { ListItemMapper.toListItemResponseTO(it) }.collectList()
                .zipWith(
                        listPreferenceRepository.getListPreference(listId, guestId).map { it.itemSortOrder!! }.switchIfEmpty { Mono.just("") }
                )
                .map {
                    // sort existing items with db sort order, and form current sort order string based on sorted existing items
                    val items = it.t1
                    val dbSortOrder: String = it.t2
                    if (dbSortOrder.isNotBlank()) {
                        sortListItemsByPosition(dbSortOrder, items).map {
                            it.listItemId }.joinToString(",")
                    } else {
                        // no dbSortOrder available, use natural order as current sort order
                        items.map { it.listItemId }.joinToString(",")
                    }
                }
                .flatMap {
                    // apply guest initiated order change and form new sort order to save in db
                    val currentItemSortOrder = it
                    val newSortOrder = editListIdPosSortOrder(primaryListItemId, secondaryListItemId, direction, currentItemSortOrder)
                    listPreferenceRepository.saveListPreference(
                            ListPreferenceEntity(guestId = guestId, listId = listId, itemSortOrder = newSortOrder))
                }.switchIfEmpty {
                    logger.error("Unable to find list $listId in the repository")
                    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                    Mono.just<ListPreferenceEntity>(null)
                }
    }

    fun deleteById(guestId: String, listId: UUID): Mono<Int> {
        return listPreferenceRepository.deleteListPreferenceByListAndGuestId(ListPreferenceEntity(listId = listId, guestId = guestId))
                .flatMap { Mono.just(1) }
                .switchIfEmpty { Mono.just(0) }
    }

    fun removeListItemIdFromSortOrder(guestId: String, listId: UUID, listItemId: UUID): Mono<ListPreferenceEntity> {
        return removeListItemIdFromSortOrder(guestId, listId, arrayOf(listItemId))
    }

    fun removeListItemIdFromSortOrder(guestId: String, listId: UUID, listItemIds: Array<UUID>?): Mono<ListPreferenceEntity> {
        if (listItemIds == null || listItemIds.isEmpty()) {
            return Mono.just(ListPreferenceEntity(listId = listId, guestId = guestId, itemSortOrder = ""))
        }

        return listPreferenceRepository.getListPreference(listId, guestId)
                .flatMap {
                    val itemIds = listItemIds.map(UUID::toString).toSet()
                    val actualItemIdList = it.itemSortOrder?.split(",")?.toMutableSet()
                    if (actualItemIdList != null && actualItemIdList.any { itemIds.contains(it) }) {
                        val newSortOrder = removeListItemIdFromSortOrder(itemIds, actualItemIdList)
                        listPreferenceRepository.saveListPreference(
                                ListPreferenceEntity(guestId = guestId, listId = listId, itemSortOrder = newSortOrder))
                    } else {
                        throw InternalServerException(LIST_ITEM_SORT_ORDER_ERROR_CODE(
                                listOf("The list item id to remove is not present" + listItemIds.joinToString(",")))) // this is not a bad request bcose of order of events
                    }
                }.switchIfEmpty { Mono.just(ListPreferenceEntity(listId = listId, guestId = guestId, itemSortOrder = "")) }
    }

    fun sortListItemsByPosition(sortOrder: String, pendingItems: List<ListItemResponseTO>): List<ListItemResponseTO> {
        val listSortOrderMap = sortOrder.split(",").mapIndexed {
            index, s -> s to index
        }.toMap()

        val wrappedList = pendingItems.map {
            var position = listSortOrderMap[it.listItemId.toString()]
            if (position == null) {
                position = Int.MAX_VALUE
            }
            ListItemResponseTOWrapper(position, it)
        }

        return wrappedList.sortedWith(compareBy { it.listPosition }).map { it.listItemResponseTO }
    }

    fun getListPreference(guestId: String, listId: UUID): Mono<ListPreferenceEntity> {
        return listPreferenceRepository.getListPreference(listId, guestId)
                .switchIfEmpty { Mono.just(ListPreferenceEntity(listId = listId, guestId = guestId, itemSortOrder = "")) }
                .onErrorResume {
                logger.error("Exception while getting list item sort order: ", it)
                Mono.just(ListPreferenceEntity(listId = listId, guestId = guestId, itemSortOrder = ""))
                }
                .map { it }
    }

    private fun addListItemIdToSortOrder(listItemId: UUID, sortOrder: String, position: Int): String {
        val list = sortOrder.split(",").toMutableList()
        list.add(position, listItemId.toString())
        val newSortOrder = list.joinToString(",")
        return if (newSortOrder.endsWith(",")) newSortOrder.substring(0, newSortOrder.lastIndex) else newSortOrder
    }

    private fun editListIdPosSortOrder(
        primaryListItemId: UUID,
        secondaryListItemId: UUID,
        direction: Direction,
        sortOrder: String
    ): String {
        val list = sortOrder.split(",").toMutableList()
        if (!list.contains(secondaryListItemId.toString())) {
            list.add(0, secondaryListItemId.toString())
        }
        list.remove(primaryListItemId.toString())
        val newPosition = if (direction == Direction.ABOVE) list.indexOf(secondaryListItemId.toString())
                else list.indexOf(secondaryListItemId.toString()) + 1
        list.add(newPosition, primaryListItemId.toString())
        val newSortOrder = list.joinToString(",")
        return if (newSortOrder.endsWith(",")) newSortOrder.substring(0, newSortOrder.lastIndex) else newSortOrder
    }

    private fun removeListItemIdFromSortOrder(listItemIds: Set<String>, sortOrders: MutableSet<String>): String {
        sortOrders.removeAll(listItemIds)
        val newSortOrder = sortOrders.joinToString(",")
        return if (newSortOrder.endsWith(",")) newSortOrder.substring(0, newSortOrder.lastIndex) else newSortOrder
    }

    /**
     * Used to assist in sorting via standard comparator
     */
    data class ListItemResponseTOWrapper(
        val listPosition: Int,
        val listItemResponseTO: ListItemResponseTO
    )
}
