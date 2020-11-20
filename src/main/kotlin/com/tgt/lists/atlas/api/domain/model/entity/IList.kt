package com.tgt.lists.atlas.api.domain.model.entity

import java.time.Instant
import java.time.LocalDate
import java.util.*

/**
 * Interface for List base data
 */
interface IList {
    var id: UUID?
    var title: String?
    var type: String?
    var subtype: String?
    var guestId: String?
    var description: String?
    var channel: String?
    var subchannel: String?
    var marker: String?
    var location: String?
    var notes: String?
    var state: String?
    var metadata: String?
    var agentId: String?
    var expiration: LocalDate?
    var createdAt: Instant?
    var updatedAt: Instant?
    var testList: Boolean?
}
