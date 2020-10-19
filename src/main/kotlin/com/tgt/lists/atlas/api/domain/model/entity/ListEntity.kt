package com.tgt.lists.atlas.api.domain.model.entity

import com.datastax.oss.driver.api.mapper.annotations.CqlName
import com.datastax.oss.driver.api.mapper.annotations.Entity
import com.datastax.oss.driver.api.mapper.annotations.NamingStrategy
import com.datastax.oss.driver.api.mapper.annotations.PartitionKey
import com.datastax.oss.driver.api.mapper.entity.naming.NamingConvention
import java.time.Instant
import java.time.LocalDate
import java.util.*

@CqlName("lists")
@Entity
@NamingStrategy(convention = [NamingConvention.SNAKE_CASE_INSENSITIVE])
data class ListEntity(
    @PartitionKey
    override var id: UUID? = null,

    override var title: String? = null,
    override var type: String? = null,
    override var subtype: String? = null,
    override var guestId: String? = null,
    override var description: String? = null,
    override var channel: String? = null,
    override var subchannel: String? = null,
    override var marker: String? = null,
    override var location: String? = null,
    override var notes: String? = null,
    override var state: String? = null,
    override var metadata: String? = null,
    override var agentId: String? = null,
    override var expiration: LocalDate? = null,
    override var createdAt: Instant? = null,
    override var updatedAt: Instant? = null,
    override var testList: Boolean? = null
) : IList