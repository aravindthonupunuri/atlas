package com.tgt.lists.atlas.api.persistence.cassandra.internal

import com.datastax.dse.driver.api.mapper.reactive.MappedReactiveResultSet
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.mapper.annotations.Dao
import com.datastax.oss.driver.api.mapper.annotations.Insert
import com.datastax.oss.driver.api.mapper.annotations.Select
import com.datastax.oss.driver.api.mapper.annotations.StatementAttributes
import com.datastax.oss.driver.api.mapper.entity.saving.NullSavingStrategy
import com.tgt.lists.atlas.api.domain.model.entity.GuestListEntity
import com.tgt.lists.micronaut.cassandra.ICassandraDao

@Dao
interface GuestListDAO : ICassandraDao {
    /**
     * Returns a BoundStatement that can be added to a batch for atomic processing of batch of statements.
     * Checkout https://docs.datastax.com/en/dse/5.1/cql/cql/cql_using/useBatch.html
     */
    @Insert(nullSavingStrategy = NullSavingStrategy.DO_NOT_SET)
    fun saveGuestList(guestListEntity: GuestListEntity): BoundStatement

    @Select
    @StatementAttributes(consistencyLevel = "ONE", pageSize = 500)
    fun findGuestListByMarker(guestId: String, listType: String, listSubtype: String, listMarker: String): MappedReactiveResultSet<GuestListEntity>
}