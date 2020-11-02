package com.tgt.lists.atlas.api.persistence.cassandra.internal

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet
import com.datastax.dse.driver.api.mapper.reactive.MappedReactiveResultSet
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.mapper.annotations.*
import com.datastax.oss.driver.api.mapper.entity.saving.NullSavingStrategy
import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListItemExtEntity
import com.tgt.lists.micronaut.cassandra.ICassandraDao
import java.util.*

@Dao
interface ListDAO : ICassandraDao {
    /**
     * Returns a BoundStatement that can be added to a batch for atomic processing of batch of statements.
     * Checkout https://docs.datastax.com/en/dse/5.1/cql/cql/cql_using/useBatch.html
     */
    @Insert(nullSavingStrategy = NullSavingStrategy.DO_NOT_SET)
    fun saveList(listEntity: ListEntity): BoundStatement

    @Insert(nullSavingStrategy = NullSavingStrategy.DO_NOT_SET)
    fun saveListItem(listItemEntity: ListItemEntity): ReactiveResultSet

    @Insert(nullSavingStrategy = NullSavingStrategy.DO_NOT_SET)
    fun saveListItemBatch(listItemEntity: ListItemEntity): BoundStatement

    @Select
    @StatementAttributes(consistencyLevel = "ONE", pageSize = 500)
    fun findListById(id: UUID): MappedReactiveResultSet<ListEntity>

    @Select
    @StatementAttributes(consistencyLevel = "ONE", pageSize = 500)
    fun findListItemsByListId(id: UUID): MappedReactiveResultSet<ListItemEntity>

    @Select(customWhereClause = "id IN :ids")
    @StatementAttributes(consistencyLevel = "ONE", pageSize = 500)
    fun findMultipleListsById(ids: List<UUID>): MappedReactiveResultSet<ListEntity>

    @Select
    @StatementAttributes(consistencyLevel = "ONE", pageSize = 500)
    fun findListAndItemsByListIdAndItemState(id: UUID, itemState: String?): MappedReactiveResultSet<ListItemExtEntity>

    @Select
    @StatementAttributes(consistencyLevel = "ONE", pageSize = 500)
    fun findListItemsByListIdAndItemState(id: UUID, itemState: String): MappedReactiveResultSet<ListItemEntity>

    @Select
    @StatementAttributes(consistencyLevel = "ONE", pageSize = 500)
    fun findListAndItemsByListId(id: UUID): MappedReactiveResultSet<ListItemExtEntity>

    @Select
    fun findListAndItemByItemId(id: UUID, itemState: String, itemId: UUID): MappedReactiveResultSet<ListItemExtEntity>

    @Select
    @StatementAttributes(consistencyLevel = "ONE", pageSize = 500)
    fun findListItemByItemId(id: UUID, itemState: String, itemId: UUID): MappedReactiveResultSet<ListItemEntity>

    @Delete
    fun deleteList(listEntity: ListEntity): BoundStatement

    @Delete
    fun deleteListItemBatch(listItemEntity: ListItemEntity): BoundStatement

    @Delete
    fun deleteListItem(listItemEntity: ListItemEntity): ReactiveResultSet
}