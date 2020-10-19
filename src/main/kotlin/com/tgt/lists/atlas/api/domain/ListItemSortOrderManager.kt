package com.tgt.lists.atlas.api.domain

import com.tgt.lists.common.components.exception.InternalServerException
import com.tgt.lists.atlas.api.domain.model.List
import com.tgt.lists.atlas.api.persistence.ListRepository
import com.tgt.lists.atlas.api.util.AppErrorCodes.LIST_ITEM_SORT_ORDER_ERROR_CODE
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
class ListItemSortOrderManager(@Inject private val listRepository: ListRepository) {

    private val logger = KotlinLogging.logger { ListItemSortOrderManager::class.java.name }

    fun saveNewListItemOrder(listId: UUID, listItemId: UUID): Mono<List> {
        return listRepository.find(listId).flatMap {
            val dbList = it
            val actualItemIdList = dbList.listItemSortOrder.split(",").toSet()
            if (actualItemIdList.contains(listItemId.toString())) {
                Mono.just(it)
            } else {
                val newSortOrder = addListItemIdToSortOrder(listItemId, it.listItemSortOrder, 0)
                listRepository.updateByListId(listId, newSortOrder).map { dbList.copy(listItemSortOrder = newSortOrder) }
            }
        }.switchIfEmpty { listRepository.save(List(listId, listItemId.toString())) }
    }

    fun updateListItemSortOrder(
        listId: UUID,
        primaryListItemId: UUID,
        secondaryListItemId: UUID,
        direction: Direction
    ): Mono<List> {
        if (primaryListItemId == secondaryListItemId) {
            return Mono.just(List(listId, ""))
        }

        return listRepository.find(listId)
            .flatMap {
                val newSortOrder = editListIdPosSortOrder(primaryListItemId,
                    secondaryListItemId, direction, it.listItemSortOrder)
                listRepository.updateByListId(listId, newSortOrder).map { List(listId, newSortOrder) }
            }
                .switchIfEmpty {
                    logger.error("Unable to find list $listId in the repository")
                    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                    Mono.just<List>(null)
                }
    }

    fun deleteById(listId: UUID): Mono<Int> {
        return listRepository.delete(listId).switchIfEmpty { Mono.just(0) }
    }

    fun removeListItemIdFromSortOrder(listId: UUID, listItemId: UUID): Mono<List> {
        return removeListItemIdFromSortOrder(listId, arrayOf(listItemId))
    }

    fun removeListItemIdFromSortOrder(listId: UUID, listItemIds: Array<UUID>?): Mono<List> {
        if (listItemIds == null || listItemIds.isEmpty()) {
            return Mono.just(List(listId, ""))
        }

        return listRepository.find(listId).flatMap {
            val itemIds = listItemIds.map(UUID::toString).toSet()
            val actualItemIdList = it.listItemSortOrder.split(",").toMutableSet()
            if (actualItemIdList.any { itemIds.contains(it) }) {
                val newSortOrder = removeListItemIdFromSortOrder(itemIds, actualItemIdList)
                listRepository.updateByListId(listId, newSortOrder).map { List(listId, newSortOrder) }
            } else {
                throw InternalServerException(LIST_ITEM_SORT_ORDER_ERROR_CODE(
                    listOf("The list item id to remove is not present" + listItemIds.joinToString(",")))) // this is not a bad request bcose of order of events
            }
        }.switchIfEmpty { Mono.just(List(listId, "")) }
    }

    fun getList(listId: UUID): Mono<List> {
        return listRepository.find(listId)
            .switchIfEmpty { Mono.just(List(listId, "")) }
            .onErrorResume {
                logger.error("Exception while getting list item sort order: ", it)
                Mono.just(List(listId, ""))
            }
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
}
