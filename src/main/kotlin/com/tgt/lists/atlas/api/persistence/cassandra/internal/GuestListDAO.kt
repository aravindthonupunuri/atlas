package com.tgt.lists.atlas.api.persistence.cassandra.internal

import com.datastax.dse.driver.api.mapper.reactive.MappedReactiveResultSet
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder
import com.datastax.oss.driver.api.mapper.annotations.*
import com.datastax.oss.driver.api.mapper.entity.saving.NullSavingStrategy
import com.tgt.lists.atlas.api.domain.model.entity.GuestListEntity
import com.tgt.lists.micronaut.cassandra.ICassandraDao
import java.util.*
import java.util.function.Function

@Dao
interface GuestListDAO : ICassandraDao {
    /**
     * Returns a BoundStatement that can be added to a batch for atomic processing of batch of statements.
     * Checkout https://docs.datastax.com/en/dse/5.1/cql/cql/cql_using/useBatch.html
     */
    @Insert(nullSavingStrategy = NullSavingStrategy.DO_NOT_SET)
    fun saveGuestList(guestListEntity: GuestListEntity): BoundStatement

    @Select
    @StatementAttributes(pageSize = 500)
    // listSubtype can be null since not all lists also has a sub-type
    fun findGuestListByMarker(guestId: String, listType: String, listSubtype: String?, listMarker: String, setAttributes: Function<BoundStatementBuilder, BoundStatementBuilder>): MappedReactiveResultSet<GuestListEntity>

    @Select
    @StatementAttributes(pageSize = 500)
    fun findGuestListByGuestId(guestId: String, listType: String?, setAttributes: Function<BoundStatementBuilder, BoundStatementBuilder>): MappedReactiveResultSet<GuestListEntity>

    @Select
    @StatementAttributes(consistencyLevel = "ONE", pageSize = 500)
    fun findGuestListsByGuestId(guestId: String, listType: String): MappedReactiveResultSet<GuestListEntity>

    @Delete(entityClass = [GuestListEntity::class])
    fun deleteByIdForId(guestId: String?, type: String?, subtype: String?, marker: String?, id: UUID?): BoundStatement

    @Select
    @StatementAttributes(pageSize = 500)
    fun findGuestListById(guestId: String, listType: String, listSubtype: String, listMarker: String, listId: UUID, setAttributes: Function<BoundStatementBuilder, BoundStatementBuilder>): MappedReactiveResultSet<GuestListEntity>
}