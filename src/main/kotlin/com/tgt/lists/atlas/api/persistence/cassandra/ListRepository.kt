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
import com.tgt.lists.micronaut.cassandra.RetryableStatementExecutor
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Singleton

@Singleton
class ListRepository(
    private val listDAO: ListDAO,
    private val guestListDAO: GuestListDAO,
    private val batchExecutor: BatchExecutor,
    private val dataContextContainerManager: DataContextContainerManager,
    private val retryableStatementExecutor: RetryableStatementExecutor
) {

    private val className = ListRepository::class.java.name

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
        return retryableStatementExecutor.read(className, "findGuestListById") { consistency ->
            guestListDAO.findGuestListById(
                    existingListEntity.guestId!!,
                    existingListEntity.type!!,
                    existingListEntity.subtype!!,
                    existingListEntity.marker!!,
                    existingListEntity.id!!,
                    consistency)
        }
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
            retryableStatementExecutor.write(className, "saveListItem") { consistency ->
                listDAO.saveListItem(updatedListItemEntity.validate(), consistency) }
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
            retryableStatementExecutor.write(className, "saveListItem") { consistency ->
                listDAO.saveListItem(listItemsEntity.first().validate(), consistency) }
        }.map { items.toList() }
    }

    fun findListById(listId: UUID): Mono<ListEntity> {
        return Mono.subscriberContext().flatMap {
            val context = it
            dataContextContainerManager.getListEntity(context, listId)?.let {
                Mono.just(it)
            } ?: retryableStatementExecutor.read(className, "findListById") { consistency ->
                listDAO.findListById(listId, consistency) }
                .map {
                dataContextContainerManager.setListEntity(context, listId, it)
                it
            }
        }
    }

    fun findMultipleListsById(listId: List<UUID>): Flux<ListEntity> {
        return retryableStatementExecutor.readFlux(className, "findMultipleListsById") { consistency ->
            listDAO.findMultipleListsById(listId, consistency) }
    }

    fun findListItemsByListId(listId: UUID): Flux<ListItemEntity> {
        return retryableStatementExecutor.readFlux(className, "findListItemsByListId") { consistency ->
            listDAO.findListItemsByListId(listId, consistency) }
    }

    fun findListAndItemsByListId(listId: UUID): Flux<ListItemExtEntity> {
        return retryableStatementExecutor.readFlux(className, "findListAndItemsByListId") { consistency ->
            listDAO.findListAndItemsByListId(listId, consistency) }
    }

    fun findListAndItemsByListIdAndItemState(listId: UUID, itemState: String): Flux<ListItemExtEntity> {
        return retryableStatementExecutor.readFlux(className, "findListAndItemsByListIdAndItemState") { consistency ->
            listDAO.findListAndItemsByListIdAndItemState(listId, itemState, consistency) }
    }

    fun findListItemsByListIdAndItemState(listId: UUID, itemState: String): Flux<ListItemEntity> {
        return retryableStatementExecutor.readFlux(className, "findListItemsByListIdAndItemState") { consistency ->
            listDAO.findListItemsByListIdAndItemState(listId, itemState, consistency) }
    }

    fun findListItemByItemId(listId: UUID, itemState: String, itemId: UUID): Mono<ListItemEntity> {
        return retryableStatementExecutor.read(className, "findListItemByItemId") { consistency ->
            listDAO.findListItemByItemId(listId, itemState, itemId, consistency) }
    }

    fun findListAndItemByItemId(listId: UUID, itemState: String, itemId: UUID): Mono<ListItemExtEntity> {
        return retryableStatementExecutor.read(className, "findListAndItemByItemId") { consistency ->
            listDAO.findListAndItemByItemId(listId, itemState, itemId, consistency) }
    }

    fun findGuestListByMarker(guestId: String, listType: String, listSubtype: String?, listMarker: String): Mono<GuestListEntity> {
        return retryableStatementExecutor.read(className, "findGuestListByMarker") { consistency ->
            guestListDAO.findGuestListByMarker(guestId, listType, listSubtype, listMarker, consistency) }
    }

    fun findGuestListsByGuestId(guestId: String, listType: String): Flux<GuestListEntity> {
        return retryableStatementExecutor.readFlux(className, "findGuestListsByGuestId") { consistency ->
            guestListDAO.findGuestListsByGuestId(guestId, listType, consistency) }
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
            retryableStatementExecutor.write(className, "deleteListItem") { consistency ->
                listDAO.deleteListItem(listItemsEntity.first(), consistency) }
        }.map { listItemsEntity }
    }

    fun findGuestLists(
        guestId: String,
        listType: String
    ): Mono<List<ListEntity>> {
        return findGuestListsByGuestId(guestId, listType).collectList().flatMap {
            if (it.isNullOrEmpty()) {
                Mono.just(emptyList())
            } else {
                findMultipleListsById(it.map { it.id!! }.toList()).collectList()
            }
        }
    }
}