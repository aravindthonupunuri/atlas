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
import reactor.kotlin.core.publisher.switchIfEmpty
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
        return Mono.from(batchExecutor.executeBatch(batchStmts, this::class.simpleName!!, "saveList")).map {
            listEntity
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
        return Mono.from(batchExecutor.executeBatch(batchStmts, this::class.simpleName!!, "deleteList"))
                .map { listEntity }
                .switchIfEmpty { Mono.just(listEntity) }
    }
}