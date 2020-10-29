package com.tgt.lists.atlas.api.domain.model.entity

import com.datastax.oss.driver.api.mapper.annotations.CqlName
import com.datastax.oss.driver.api.mapper.annotations.Entity
import com.datastax.oss.driver.api.mapper.annotations.NamingStrategy
import com.datastax.oss.driver.api.mapper.annotations.PartitionKey
import com.datastax.oss.driver.api.mapper.entity.naming.NamingConvention

@CqlName("guest_preference")
@Entity
@NamingStrategy(convention = [NamingConvention.SNAKE_CASE_INSENSITIVE])
data class GuestPreferenceEntity(
    @PartitionKey
    var guestId: String? = null,

    // this isn't ideal but a simpler approach; comma separated list uuids of only those which were sorted by guest
    var listSortOrder: String? = null
)