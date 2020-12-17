package com.tgt.lists.atlas.api.persistence.cassandra.internal

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet
import com.datastax.dse.driver.api.mapper.reactive.MappedReactiveResultSet
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder
import com.datastax.oss.driver.api.mapper.annotations.Dao
import com.datastax.oss.driver.api.mapper.annotations.Insert
import com.datastax.oss.driver.api.mapper.annotations.Select
import com.datastax.oss.driver.api.mapper.annotations.StatementAttributes
import com.datastax.oss.driver.api.mapper.entity.saving.NullSavingStrategy
import com.tgt.lists.atlas.api.domain.model.entity.GuestPreferenceEntity
import com.tgt.lists.micronaut.cassandra.ICassandraDao
import io.micronaut.context.annotation.Context
import java.util.function.Function

@Dao
@Context
interface GuestPreferenceDAO : ICassandraDao {

    @Insert(nullSavingStrategy = NullSavingStrategy.DO_NOT_SET)
    fun saveGuestPreference(guestPreferenceEntity: GuestPreferenceEntity, setAttributes: Function<BoundStatementBuilder, BoundStatementBuilder>): ReactiveResultSet

    @Select
    @StatementAttributes(pageSize = 500)
    fun findGuestPreference(guestId: String, setAttributes: Function<BoundStatementBuilder, BoundStatementBuilder>): MappedReactiveResultSet<GuestPreferenceEntity>
}