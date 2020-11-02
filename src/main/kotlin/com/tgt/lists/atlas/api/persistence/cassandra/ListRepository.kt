package com.tgt.lists.atlas.api.persistence.cassandra

import com.datastax.oss.driver.api.core.cql.BatchableStatement
import com.tgt.lists.atlas.api.domain.model.entity.GuestListEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListItemExtEntity
import com.tgt.lists.atlas.api.persistence.DataContextContainerManager
import com.tgt.lists.atlas.api.persistence.cassandra.internal.GuestListDAO
import com.tgt.lists.atlas.api.persistence.cassandra.internal.ListDAO
import com.tgt.lists.atlas.api.util.getLocalInstant
import com.tgt.lists.micronaut.cassandra.BatchExecutor
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Singleton

@Singleton
class ListRepository(
    private val listDAO: ListDAO,
    private val guestListDAO: GuestListDAO,
    private val batchExecutor: BatchExecutor,
    private val dataContextContainerManager: DataContextContainerManager
) {
    fun saveList(listEntity: ListEntity): Mono<ListEntity> {
        if (listEntity.createdAt == null) {
            // new list getting created
            listEntity.createdAt = getLocalInstant()
            listEntity.updatedAt = listEntity.createdAt
        } else {
            // existing list update
            listEntity.updatedAt = getLocalInstant()
        }

        val guestListEntity = GuestListEntity(
                guestId = listEntity.guestId,
                type = listEntity.type,
                subtype = listEntity.subtype,
                marker = listEntity.marker,
                id = listEntity.id,
                state = listEntity.state
        )
        val batchStmts = listOf<BatchableStatement<*>>(
                listDAO.saveList(listEntity),
                guestListDAO.saveGuestList(guestListEntity)
        )
        return batchExecutor.executeBatch(batchStmts, this::class.simpleName!!, "saveList")
                .map { listEntity }
    }

    fun updateList(existingListEntity: ListEntity, updatedListEntity: ListEntity): Mono<ListEntity> {
        // existing list update
        updatedListEntity.updatedAt = getLocalInstant()

        val updatedGuestListEntity = GuestListEntity(
                guestId = updatedListEntity.guestId,
                type = updatedListEntity.type,
                subtype = updatedListEntity.subtype,
                marker = updatedListEntity.marker,
                id = updatedListEntity.id,
                state = updatedListEntity.state
        )

        val batchStmts = mutableListOf<BatchableStatement<*>>(
                listDAO.saveList(updatedListEntity)
        )

        // add saveGuestList statement iff either marker OR state has changed against existing value
        return Mono.from(guestListDAO.findGuestListById(
                existingListEntity.guestId!!,
                existingListEntity.type!!,
                existingListEntity.subtype!!,
                existingListEntity.marker!!,
                existingListEntity.id!!)
        )
                .flatMap {
                    val existingGuestListEntity = it
                    if (existingGuestListEntity.state != updatedGuestListEntity.state ||
                            existingGuestListEntity.marker != updatedGuestListEntity.marker) {
                        // marker is a ClusteringColumn and update statement isn't supported so delete and save are added to batch execution
                        batchStmts.add(guestListDAO.deleteByIdForId(
                                existingGuestListEntity.guestId,
                                existingGuestListEntity.type,
                                existingGuestListEntity.subtype,
                                existingGuestListEntity.marker,
                                existingGuestListEntity.id))
                        batchStmts.add(guestListDAO.saveGuestList(updatedGuestListEntity))
                    }
                    batchExecutor.executeBatch(batchStmts, this::class.simpleName!!, "updateList")
                            .map { updatedListEntity }
                }
    }

    fun updateListItem(updatedListItemEntity: ListItemEntity, existingListItemEntity: ListItemEntity?): Mono<ListItemEntity> {
        // existing list item update
        updatedListItemEntity.itemUpdatedAt = getLocalInstant()
        return if (existingListItemEntity != null && existingListItemEntity.itemState != updatedListItemEntity.itemState) {
            val batchStmts = arrayListOf<BatchableStatement<*>>()
            batchStmts.add(listDAO.deleteListItemBatch(existingListItemEntity))
            batchStmts.add(listDAO.saveListItemBatch(updatedListItemEntity.validate()))
            batchExecutor.executeBatch(batchStmts, this::class.simpleName!!, "updateListItem")
        } else {
            Mono.from(listDAO.saveListItem(updatedListItemEntity.validate()))
        }.map { updatedListItemEntity }
    }

    fun saveListItems(listItemsEntity: List<ListItemEntity>): Mono<List<ListItemEntity>> {
        val now = getLocalInstant()
        val items = arrayListOf<ListItemEntity>()
        listItemsEntity.map {
            if (it.itemCreatedAt == null) {
                // new list item getting created
                items.add(it.copy(itemCreatedAt = now, itemUpdatedAt = it.itemCreatedAt))
            } else {
                // existing list item update
                items.add(it.copy(itemUpdatedAt = now))
            }
        }

        return if (items.size > 1) {
            val batchStmts = arrayListOf<BatchableStatement<*>>()
            listItemsEntity.map { batchStmts.add(listDAO.saveListItemBatch(it.validate())) }
            batchExecutor.executeBatch(batchStmts, this::class.simpleName!!, "saveListItems")
        } else {
            Mono.from(listDAO.saveListItem(listItemsEntity.first().validate()))
        }.map { items.toList() }
    }

    fun findListById(listId: UUID): Mono<ListEntity> {
        return Mono.subscriberContext().flatMap {
            val context = it
            dataContextContainerManager.getListEntity(context, listId)?.let {
                Mono.just(it)
            } ?: Mono.from(listDAO.findListById(listId)).map {
                dataContextContainerManager.setListEntity(context, listId, it)
                it
            }
        }
    }

    fun findMultipleListsById(listId: List<UUID>): Flux<ListEntity> {
        return Flux.from(listDAO.findMultipleListsById(listId))
    }

    fun findListItemsByListId(listId: UUID): Flux<ListItemEntity> {
        return Flux.from(listDAO.findListItemsByListId(listId))
    }

    fun findListAndItemsByListId(listId: UUID): Flux<ListItemExtEntity> {
        return Flux.from(listDAO.findListAndItemsByListId(listId))
    }

    fun findListAndItemsByListIdAndItemState(listId: UUID, itemState: String): Flux<ListItemExtEntity> {
        return Flux.from(listDAO.findListAndItemsByListIdAndItemState(listId, itemState))
    }

    fun findListItemsByListIdAndItemState(listId: UUID, itemState: String): Flux<ListItemEntity> {
        return Flux.from(listDAO.findListItemsByListIdAndItemState(listId, itemState))
    }

    fun findListItemByItemId(listId: UUID, itemState: String, itemId: UUID): Mono<ListItemEntity> {
        return Mono.from(listDAO.findListItemByItemId(listId, itemState, itemId))
    }

    fun findListAndItemByItemId(listId: UUID, itemState: String, itemId: UUID): Mono<ListItemExtEntity> {
        return Mono.from(listDAO.findListAndItemByItemId(listId, itemState, itemId))
    }

    fun findGuestListByMarker(guestId: String, listType: String, listSubtype: String?, listMarker: String): Mono<GuestListEntity> {
        return Mono.from(guestListDAO.findGuestListByMarker(guestId, listType, listSubtype, listMarker))
    }

    fun findGuestListsByGuestId(guestId: String, listType: String): Flux<GuestListEntity> {
        return Flux.from(guestListDAO.findGuestListsByGuestId(guestId, listType))
    }

    fun deleteList(listEntity: ListEntity): Mono<ListEntity> {
        val batchStmts = listOf<BatchableStatement<*>>(
                listDAO.deleteList(listEntity),
                guestListDAO.deleteByIdForId(listEntity.guestId!!, listEntity.type!!, listEntity.subtype!!, listEntity.marker!!, listEntity.id!!)
        )
        return batchExecutor.executeBatch(batchStmts, this::class.simpleName!!, "deleteList")
                .map { listEntity }
    }

    fun deleteListItems(listItemsEntity: List<ListItemEntity>): Mono<List<ListItemEntity>> {
        return if (listItemsEntity.size > 1) {
            val batchStmts = arrayListOf<BatchableStatement<*>>()
            listItemsEntity.map { batchStmts.add(listDAO.deleteListItemBatch(it)) }
            batchExecutor.executeBatch(batchStmts, this::class.simpleName!!, "deleteListItem")
        } else {
            Mono.from(listDAO.deleteListItem(listItemsEntity.first()))
        }.map { listItemsEntity }
    }
}