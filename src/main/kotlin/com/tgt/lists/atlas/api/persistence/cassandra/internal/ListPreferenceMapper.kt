package com.tgt.lists.atlas.api.persistence.cassandra.internal

import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.mapper.annotations.DaoFactory
import com.datastax.oss.driver.api.mapper.annotations.DaoKeyspace
import com.datastax.oss.driver.api.mapper.annotations.Mapper

@Mapper
interface ListPreferenceMapper {
    @DaoFactory
    fun listPreferenceDao(@DaoKeyspace keyspace: CqlIdentifier): ListPreferenceDAO
}