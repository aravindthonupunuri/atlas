package com.tgt.lists.atlas.api.persistence.cassandra.internal

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet
import com.datastax.dse.driver.api.mapper.reactive.MappedReactiveResultSet
import com.datastax.oss.driver.api.mapper.annotations.*
import com.datastax.oss.driver.api.mapper.entity.saving.NullSavingStrategy
import com.tgt.lists.atlas.api.domain.model.entity.ListPreferenceEntity
import com.tgt.lists.micronaut.cassandra.ICassandraDao
import java.util.*

@Dao
interface ListPreferenceDAO : ICassandraDao {

    @Insert(nullSavingStrategy = NullSavingStrategy.DO_NOT_SET)
    fun saveListPreference(listPreferenceEntity: ListPreferenceEntity): ReactiveResultSet

    @Select
    @StatementAttributes(consistencyLevel = "ONE", pageSize = 500)
    fun findListPreferenceByListAndGuestId(listId: UUID, guestId: String): MappedReactiveResultSet<ListPreferenceEntity>

    @Delete
    fun deleteListPreferenceByListAndGuestId(listPreferenceEntity: ListPreferenceEntity): ReactiveResultSet
}