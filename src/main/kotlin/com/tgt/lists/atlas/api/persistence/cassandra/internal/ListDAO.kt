package com.tgt.lists.atlas.api.persistence.cassandra.internal

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet
import com.datastax.dse.driver.api.mapper.reactive.MappedReactiveResultSet
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder
import com.datastax.oss.driver.api.mapper.annotations.*
import com.datastax.oss.driver.api.mapper.entity.saving.NullSavingStrategy
import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListItemExtEntity
import com.tgt.lists.micronaut.cassandra.ICassandraDao
import io.micronaut.context.annotation.Context
import java.util.*
import java.util.function.Function

@Dao
@Context
interface ListDAO : ICassandraDao {
    /**
     * Returns a BoundStatement that can be added to a batch for atomic processing of batch of statements.
     * Checkout https://docs.datastax.com/en/dse/5.1/cql/cql/cql_using/useBatch.html
     */
    @Insert(nullSavingStrategy = NullSavingStrategy.DO_NOT_SET)
    fun saveList(listEntity: ListEntity): BoundStatement

    @Insert(nullSavingStrategy = NullSavingStrategy.DO_NOT_SET)
    fun saveListItem(listItemEntity: ListItemEntity, setAttributes: Function<BoundStatementBuilder, BoundStatementBuilder>): ReactiveResultSet

    @Insert(nullSavingStrategy = NullSavingStrategy.DO_NOT_SET)
    fun saveListItemBatch(listItemEntity: ListItemEntity): BoundStatement

    @Select
    @StatementAttributes(pageSize = 500)
    fun findListById(id: UUID, setAttributes: Function<BoundStatementBuilder, BoundStatementBuilder>): MappedReactiveResultSet<ListEntity>

    @Select
    @StatementAttributes(pageSize = 500)
    fun findListItemsByListId(id: UUID, setAttributes: Function<BoundStatementBuilder, BoundStatementBuilder>): MappedReactiveResultSet<ListItemEntity>

    @Query("SELECT distinct id,title,type,subtype,guest_id,description,expiration,channel,subchannel,marker,location,notes,state,metadata,agent_id,created_at,updated_at,test_list FROM \${qualifiedTableId} WHERE id IN :ids")
    fun findLists(ids: List<UUID>, setAttributes: Function<BoundStatementBuilder, BoundStatementBuilder>): MappedReactiveResultSet<ListEntity>

    @Select
    @StatementAttributes(pageSize = 500)
    fun findListAndItemsByListIdAndItemState(id: UUID, itemState: String?, setAttributes: Function<BoundStatementBuilder, BoundStatementBuilder>): MappedReactiveResultSet<ListItemExtEntity>

    @Select
    @StatementAttributes(pageSize = 500)
    fun findListItemsByListIdAndItemState(id: UUID, itemState: String, setAttributes: Function<BoundStatementBuilder, BoundStatementBuilder>): MappedReactiveResultSet<ListItemEntity>

    @Select
    @StatementAttributes(pageSize = 500)
    fun findListAndItemsByListId(id: UUID, setAttributes: Function<BoundStatementBuilder, BoundStatementBuilder>): MappedReactiveResultSet<ListItemExtEntity>

    @Select
    @StatementAttributes(pageSize = 500)
    fun findListItemByItemId(id: UUID, itemState: String, itemId: UUID, setAttributes: Function<BoundStatementBuilder, BoundStatementBuilder>): MappedReactiveResultSet<ListItemEntity>

    @Delete
    fun deleteList(listEntity: ListEntity): BoundStatement

    @Delete
    fun deleteListItemBatch(listItemEntity: ListItemEntity): BoundStatement

    @Delete
    fun deleteListItem(listItemEntity: ListItemEntity, setAttributes: Function<BoundStatementBuilder, BoundStatementBuilder>): ReactiveResultSet
}