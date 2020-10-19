package com.tgt.lists.atlas.api.domain.model

import io.micronaut.data.annotation.DateCreated
import io.micronaut.data.annotation.DateUpdated
import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "guest_preference")
data class GuestPreference(
    @Id
    @Column(name = "guest_id")
    val guestId: String,

    @Column(name = "list_sort_order")
    val listSortOrder: String, // this is bad. we know. simple approach vs complicated at this point. comma separated list uuid.

    @DateCreated
    @Column(name = "date_created")
    var dateCreated: LocalDateTime? = null,

    @DateUpdated
    @Column(name = "date_updated")
    var dateUpdated: LocalDateTime? = null
)
