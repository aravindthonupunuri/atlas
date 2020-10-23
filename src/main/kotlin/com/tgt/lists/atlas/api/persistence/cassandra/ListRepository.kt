package com.tgt.lists.atlas.api.persistence.cassandra

import com.datastax.oss.driver.api.core.cql.BatchableStatement
import com.tgt.lists.atlas.api.domain.model.entity.GuestListEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListItemExtEntity
import com.tgt.lists.atlas.api.persistence.DataContextContainerManager
import com.tgt.lists.atlas.api.persistence.cassandra.internal.GuestListDAO
import com.tgt.lists.atlas.api.persistence.cassandra.internal.ListDAO
import com.tgt.lists.micronaut.cassandra.BatchExecutor
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.ZonedDateTime
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
        val now = ZonedDateTime.now()
        if (listEntity.createdAt == null) {
            // new list getting created
            listEntity.createdAt = now.toInstant()
            listEntity.updatedAt = listEntity.createdAt
        } else {
            // existing list update
            listEntity.updatedAt = now.toInstant()
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
        updatedListEntity.updatedAt = ZonedDateTime.now().toInstant()

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

    fun saveListItem(listItemEntity: ListItemEntity): Mono<ListItemEntity> {
        val now = ZonedDateTime.now()
        if (listItemEntity.itemCreatedAt == null) {
            // new list item getting created
            listItemEntity.itemCreatedAt = now.toInstant()
            listItemEntity.itemUpdatedAt = listItemEntity.itemCreatedAt
        } else {
            // existing list item update
            listItemEntity.itemUpdatedAt = now.toInstant()
        }
        return Mono.from(listDAO.saveListItem(listItemEntity)).map {
            listItemEntity
        }
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

    fun findListItemsByListId(listId: UUID): Flux<ListItemEntity> {
        return Flux.from(listDAO.findListItemsByListId(listId))
    }

    fun findListItemsByListIdAndItemState(listId: UUID, itemState: String): Flux<ListItemEntity> {
        return Flux.from(listDAO.findListItemsByListIdAndItemState(listId, itemState))
    }

    fun findListItemByItemId(listId: UUID, itemState: String, itemId: UUID): Mono<ListItemExtEntity> {
        return Mono.from(listDAO.findListItemByItemId(listId, itemState, itemId))
    }

    fun findGuestListByMarker(guestId: String, listType: String, listSubtype: String, listMarker: String): Mono<GuestListEntity> {
        return Mono.from(guestListDAO.findGuestListByMarker(guestId, listType, listSubtype, listMarker))
    }

    fun deleteList(listEntity: ListEntity): Mono<ListEntity> {
        val batchStmts = listOf<BatchableStatement<*>>(
                listDAO.deleteList(listEntity),
                guestListDAO.deleteByIdForId(listEntity.guestId!!, listEntity.type!!, listEntity.subtype!!, listEntity.marker!!, listEntity.id!!)
        )
        return batchExecutor.executeBatch(batchStmts, this::class.simpleName!!, "deleteList")
                .map { listEntity }
    }
}