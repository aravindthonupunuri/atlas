package com.tgt.lists.atlas.api.persistence

import com.tgt.lists.atlas.api.domain.model.GuestPreference
import io.micronaut.data.annotation.Id
import reactor.core.publisher.Mono

interface GuestPreferenceRepository {
    fun save(guestPreference: GuestPreference): Mono<GuestPreference>
    fun find(@Id guestId: String): Mono<GuestPreference>
    fun update(@Id guestId: String, listSortOrder: String): Mono<Int>
}
