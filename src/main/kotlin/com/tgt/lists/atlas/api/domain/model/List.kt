package com.tgt.lists.atlas.api.domain.model

import io.micronaut.data.annotation.DateCreated
import io.micronaut.data.annotation.DateUpdated
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.model.DataType
import java.time.LocalDateTime
import java.util.*
import javax.persistence.Column
import javax.persistence.Id
import javax.persistence.Table

@MappedEntity
@Table(name = "list")
data class List(
    @Id
    @Column(name = "list_id")
    @MappedProperty(type = DataType.OBJECT)
    val listId: UUID,

    @Column(name = "list_item_sort_order")
    val listItemSortOrder: String, // this is bad. we know. simple approach vs complicated at this point. comma separated list uuid.

    @DateCreated
    @Column(name = "date_created")
    var dateCreated: LocalDateTime? = null,

    @DateUpdated
    @Column(name = "date_updated")
    var dateUpdated: LocalDateTime? = null
)
