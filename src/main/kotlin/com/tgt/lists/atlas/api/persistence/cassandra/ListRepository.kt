package com.tgt.lists.atlas.api.persistence.cassandra

import com.datastax.oss.driver.api.core.cql.BatchableStatement
import com.tgt.lists.atlas.api.domain.model.entity.GuestListEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListItemExtEntity
import com.tgt.lists.atlas.api.persistence.DataContextContainerManager
import com.tgt.lists.atlas.api.persistence.cassandra.internal.GuestListDAO
import com.tgt.lists.atlas.api.persistence.cassandra.internal.ListDAO
import com.tgt.lists.atlas.api.type.LIST_ITEM_STATE
import com.tgt.lists.atlas.api.util.getLocalInstant
import com.tgt.lists.micronaut.cassandra.BatchExecutor
import com.tgt.lists.micronaut.cassandra.RetryableStatementExecutor
import mu.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
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
    private val logger = KotlinLogging.logger { className }

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
                listDAO.saveListItem(listItemsEntity.first().validate(), consistency) }.map { true }.switchIfEmpty { Mono.just(true) } // TODO: Move switchIfEmpty to library
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

    fun findGuestLists(guestId: String, listType: String): Mono<List<ListEntity>> {
        return findGuestListsByGuestId(guestId, listType).collectList().flatMap {
            if (it.isNullOrEmpty()) {
                Mono.just(emptyList())
            } else {
                findLists(it.map { it.id!! }.toSet()).collectList()
            }
        }
    }

    fun findLists(listId: Set<UUID>): Flux<ListEntity> {
        return retryableStatementExecutor.readFlux(className, "findMultipleListsById") { consistency ->
            listDAO.findLists(listId.toList(), consistency) }
    }

    fun findListItemByItemId(listId: UUID, itemId: UUID): Mono<ListItemEntity> {
        return findListItemByItemIdAndState(listId, itemId, LIST_ITEM_STATE.PENDING.value)
                .switchIfEmpty {
                    logger.debug { "Item is not found in pending state, check for item in completed state" }
                    findListItemByItemIdAndState(listId, itemId, LIST_ITEM_STATE.COMPLETED.value)
                }
    }

    fun findListItemByItemIdAndState(listId: UUID, itemId: UUID, itemState: String): Mono<ListItemEntity> {
        return retryableStatementExecutor.read(className, "findListItemByItemId") { consistency ->
            listDAO.findListItemByItemId(listId, itemState, itemId, consistency) }
    }

    fun findListItemsByListId(listId: UUID): Flux<ListItemEntity> {
        return retryableStatementExecutor.readFlux(className, "findListItemsByListId") { consistency ->
            listDAO.findListItemsByListId(listId, consistency) }.filter { it.itemId != null }
    }

    fun findListItemsByListIdAndItemState(listId: UUID, itemState: String): Flux<ListItemEntity> {
        return retryableStatementExecutor.readFlux(className, "findListItemsByListIdAndItemState") { consistency ->
            listDAO.findListItemsByListIdAndItemState(listId, itemState, consistency) }
    }

    fun findListAndItemsByListId(listId: UUID): Flux<ListItemExtEntity> {
        return retryableStatementExecutor.readFlux(className, "findListAndItemsByListId") { consistency ->
            listDAO.findListAndItemsByListId(listId, consistency) }.filter { it.itemId != null }
    }

    fun findListAndItemsByListIdAndItemState(listId: UUID, itemState: String): Flux<ListItemExtEntity> {
        return retryableStatementExecutor.readFlux(className, "findListAndItemsByListIdAndItemState") { consistency ->
            listDAO.findListAndItemsByListIdAndItemState(listId, itemState, consistency) }
    }

    fun findGuestListByMarker(guestId: String, listType: String, listSubtype: String, listMarker: String): Mono<GuestListEntity> {
        return retryableStatementExecutor.read(className, "findGuestListByMarker") { consistency ->
            guestListDAO.findGuestListByMarker(guestId, listType, listSubtype, listMarker, consistency) }
    }

    fun findGuestListsByGuestId(guestId: String, listType: String): Flux<GuestListEntity> {
        return retryableStatementExecutor.readFlux(className, "findGuestListsByGuestId") { consistency ->
            guestListDAO.findGuestListsByGuestId(guestId, listType, consistency) }
    }

    fun updateList(existingListEntity: ListEntity, updatedListEntity: ListEntity): Mono<ListEntity> {
        var guestListAdded = false

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

        val batchStmts = mutableListOf<BatchableStatement<*>>(listDAO.saveList(updatedListEntity))

        // add saveGuestList statement if state is updated
        if (existingListEntity.state != updatedListEntity.state) {
            batchStmts.add(guestListDAO.saveGuestList(updatedGuestListEntity))
            guestListAdded = true
        }

        // marker is a ClusteringColumn; drop the row and add it back again with updated marker
        return retryableStatementExecutor.read(className, "findGuestListById") { consistency ->
            guestListDAO.findGuestListById(
                    existingListEntity.guestId!!,
                    existingListEntity.type!!,
                    existingListEntity.subtype!!,
                    existingListEntity.marker!!,
                    existingListEntity.id!!,
                    consistency
            ) }
                .flatMap {
                    val existingGuestListEntity = it

                    if (existingGuestListEntity.marker != updatedGuestListEntity.marker) {
                        batchStmts.add(
                                guestListDAO.deleteByIdForId(existingGuestListEntity.guestId,
                                        existingGuestListEntity.type,
                                        existingGuestListEntity.subtype,
                                        existingGuestListEntity.marker,
                                        existingGuestListEntity.id)
                        )
                        // if guestList is already added don't duplicate
                        if (!guestListAdded) batchStmts.add(guestListDAO.saveGuestList(updatedGuestListEntity))
                    }
                    batchExecutor.executeBatch(batchStmts, this::class.simpleName!!, "updateList")
                            .map { updatedListEntity }
                }
    }

    fun updateListItem(updatedListItemEntity: ListItemEntity, existingListItemEntity: ListItemEntity?): Mono<ListItemEntity> {
        updatedListItemEntity.itemUpdatedAt = getLocalInstant()
        // existingItem entity not passed when its called from Deduplication Manager
        // If the state is getting updated, we need to delete the existing entity and create a new entity since itemState is part of the primary key hence cannot be updated
        return if (existingListItemEntity != null && existingListItemEntity.itemState != updatedListItemEntity.itemState) {
            val batchStmts = arrayListOf<BatchableStatement<*>>()
            batchStmts.add(listDAO.deleteListItemBatch(existingListItemEntity))
            batchStmts.add(listDAO.saveListItemBatch(updatedListItemEntity.validate()))
            batchExecutor.executeBatch(batchStmts, this::class.simpleName!!, "updateListItem")
        } else {
            retryableStatementExecutor.write(className, "updateListItem") { consistency ->
                listDAO.saveListItem(updatedListItemEntity.validate(), consistency) }.map { true }.switchIfEmpty { Mono.just(true) } // TODO: Move switchIfEmpty to library
        }.map { updatedListItemEntity }
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
                listDAO.deleteListItem(listItemsEntity.first(), consistency) }.map { true }.switchIfEmpty { Mono.just(true) } // TODO: Move switchIfEmpty to library
        }.map { listItemsEntity }
    }
}