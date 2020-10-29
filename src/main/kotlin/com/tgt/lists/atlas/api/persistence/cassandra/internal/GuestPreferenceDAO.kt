package com.tgt.lists.atlas.api.persistence.cassandra.internal

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet
import com.datastax.dse.driver.api.mapper.reactive.MappedReactiveResultSet
import com.datastax.oss.driver.api.mapper.annotations.Dao
import com.datastax.oss.driver.api.mapper.annotations.Insert
import com.datastax.oss.driver.api.mapper.annotations.Select
import com.datastax.oss.driver.api.mapper.annotations.StatementAttributes
import com.datastax.oss.driver.api.mapper.entity.saving.NullSavingStrategy
import com.tgt.lists.atlas.api.domain.model.entity.GuestPreferenceEntity
import com.tgt.lists.micronaut.cassandra.ICassandraDao

@Dao
interface GuestPreferenceDAO : ICassandraDao {

    @Insert(nullSavingStrategy = NullSavingStrategy.DO_NOT_SET)
    fun saveGuestPreference(guestPreferenceEntity: GuestPreferenceEntity): ReactiveResultSet

    @Select
    @StatementAttributes(consistencyLevel = "ONE", pageSize = 500)
    fun findGuestPreference(guestId: String): MappedReactiveResultSet<GuestPreferenceEntity>
}