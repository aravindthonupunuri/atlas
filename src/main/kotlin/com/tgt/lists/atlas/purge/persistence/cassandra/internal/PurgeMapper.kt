package com.tgt.lists.atlas.purge.persistence.cassandra.internal

import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.mapper.annotations.DaoFactory
import com.datastax.oss.driver.api.mapper.annotations.DaoKeyspace
import com.datastax.oss.driver.api.mapper.annotations.Mapper
import io.micronaut.context.annotation.Requires

@Requires(property = "beacon.client.enabled", value = "true")
@Mapper
interface PurgeMapper {
    @DaoFactory
    fun purgeDAO(@DaoKeyspace keyspace: CqlIdentifier): PurgeDAO
}