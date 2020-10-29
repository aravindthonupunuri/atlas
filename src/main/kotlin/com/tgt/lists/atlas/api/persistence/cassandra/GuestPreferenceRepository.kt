package com.tgt.lists.atlas.api.persistence.cassandra

import com.tgt.lists.atlas.api.domain.model.entity.GuestPreferenceEntity
import com.tgt.lists.atlas.api.persistence.cassandra.internal.GuestPreferenceDAO
import com.tgt.lists.atlas.api.util.GuestId
import reactor.core.publisher.Mono
import javax.inject.Singleton

@Singleton
class GuestPreferenceRepository(
    private val guestPreferenceDAO: GuestPreferenceDAO
) {
    fun saveGuestPreference(guestPreferenceEntity: GuestPreferenceEntity): Mono<GuestPreferenceEntity> {
        return Mono.from(guestPreferenceDAO.saveGuestPreference(guestPreferenceEntity))
                .map { guestPreferenceEntity }
    }

    fun findGuestPreference(guestId: GuestId): Mono<GuestPreferenceEntity> {
        return Mono.from(guestPreferenceDAO.findGuestPreference(guestId))
    }
}