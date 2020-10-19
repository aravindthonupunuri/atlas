package com.tgt.lists.atlas.api.domain.model.entity

import com.datastax.oss.driver.api.mapper.annotations.*
import com.datastax.oss.driver.api.mapper.entity.naming.NamingConvention
import java.time.Instant
import java.util.*

@CqlName("lists")
@Entity
@NamingStrategy(convention = [NamingConvention.SNAKE_CASE_INSENSITIVE])
data class ListItemEntity(
    @PartitionKey
    var id: UUID? = null,

    @ClusteringColumn(0)
    override var itemState: String? = null,

    @ClusteringColumn(1)
    override var itemId: UUID? = null,

    override var itemRefId: String? = null,
    override var itemType: String? = null,
    override var itemTcin: String? = null,
    override var itemDpci: String? = null,
    override var itemBarcode: String? = null,
    override var itemDesc: String? = null,
    override var itemChannel: String? = null,
    override var itemSubchannel: String? = null,
    override var itemMetadata: String? = null,
    override var itemNotes: String? = null,
    override var itemQty: Int? = null,
    override var itemQtyUom: String? = null,
    override var itemReqQty: Int? = null,
    override var itemCreatedAt: Instant? = null,
    override var itemUpdatedAt: Instant? = null
) : IListItem