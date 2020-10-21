package com.tgt.lists.atlas.api.domain.model.entity

import com.datastax.oss.driver.api.mapper.annotations.*
import com.datastax.oss.driver.api.mapper.entity.naming.NamingConvention
import java.util.*

@CqlName("guest_lists")
@Entity
@NamingStrategy(convention = [NamingConvention.SNAKE_CASE_INSENSITIVE])
data class GuestListEntity(
    @PartitionKey
    var guestId: String? = null,

    @ClusteringColumn(0)
    var type: String? = null,

    @ClusteringColumn(1)
    var subtype: String? = null,

    @ClusteringColumn(2)
    var marker: String? = null,

    @ClusteringColumn(3)
    var id: UUID? = null,

    var state: String? = null
)