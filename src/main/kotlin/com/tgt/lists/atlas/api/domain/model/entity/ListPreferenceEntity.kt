package com.tgt.lists.atlas.api.domain.model.entity

import com.datastax.oss.driver.api.mapper.annotations.*
import com.datastax.oss.driver.api.mapper.entity.naming.NamingConvention
import java.util.*

@CqlName("list_preference")
@Entity
@NamingStrategy(convention = [NamingConvention.SNAKE_CASE_INSENSITIVE])
data class ListPreferenceEntity(
    @PartitionKey
    var listId: UUID? = null,

    @ClusteringColumn(0)
    var guestId: String? = null,

    // this isn't ideal but a simpler approach; comma separated item uuids of only those which were sorted by guest
    var itemSortOrder: String? = null
)