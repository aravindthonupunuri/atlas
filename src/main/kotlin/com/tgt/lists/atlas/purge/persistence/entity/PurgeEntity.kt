package com.tgt.lists.atlas.purge.persistence.entity

import com.datastax.oss.driver.api.mapper.annotations.*
import com.datastax.oss.driver.api.mapper.entity.naming.NamingConvention
import java.time.LocalDate
import java.util.*

@CqlName("purge_expiration")
@Entity
@NamingStrategy(convention = [NamingConvention.SNAKE_CASE_INSENSITIVE])
data class PurgeEntity(
    @PartitionKey(0)
var expiration: LocalDate? = null,

    @PartitionKey(1)
var bucket: Int? = null,

    @ClusteringColumn(0)
var listId: UUID? = null
)