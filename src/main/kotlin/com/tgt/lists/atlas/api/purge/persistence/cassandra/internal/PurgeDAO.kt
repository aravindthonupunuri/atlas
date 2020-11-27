package com.tgt.lists.atlas.api.purge.persistence.cassandra.internal

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet
import com.datastax.dse.driver.api.mapper.reactive.MappedReactiveResultSet
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder
import com.datastax.oss.driver.api.mapper.annotations.Dao
import com.datastax.oss.driver.api.mapper.annotations.Insert
import com.datastax.oss.driver.api.mapper.annotations.Select
import com.datastax.oss.driver.api.mapper.entity.saving.NullSavingStrategy
import com.tgt.lists.atlas.api.purge.persistence.entity.PurgeEntity
import com.tgt.lists.micronaut.cassandra.ICassandraDao
import io.micronaut.context.annotation.Requires
import java.time.LocalDate
import java.util.*
import java.util.function.Function

@Requires(property = "beacon.client.enabled", value = "true")
@Dao
interface PurgeDAO : ICassandraDao {

    @Insert(nullSavingStrategy = NullSavingStrategy.DO_NOT_SET)
    fun savePurgeExpiration(purgeEntity: PurgeEntity, setAttributes: Function<BoundStatementBuilder, BoundStatementBuilder>): ReactiveResultSet

    @Select(customWhereClause = "expiration=:expiration AND bucket IN :bucket")
    fun findPurgeExpiration(expiration: LocalDate, bucket: List<Int>, setAttributes: Function<BoundStatementBuilder, BoundStatementBuilder>): MappedReactiveResultSet<PurgeEntity>

    @Select(customWhereClause = "expiration=:expiration AND bucket IN :bucket AND list_id=:listId")
    fun findPurgeExpirationByListId(expiration: LocalDate, bucket: List<Int>, listId: UUID, setAttributes: Function<BoundStatementBuilder, BoundStatementBuilder>): MappedReactiveResultSet<PurgeEntity>
}
