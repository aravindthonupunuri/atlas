package com.tgt.lists.atlas.api.persistence.cassandra

import com.tgt.lists.atlas.api.domain.model.entity.GuestPreferenceEntity
import com.tgt.lists.atlas.api.persistence.cassandra.internal.GuestPreferenceDAO
import com.tgt.lists.atlas.api.util.GuestId
import com.tgt.lists.micronaut.cassandra.RetryableStatementExecutor
import reactor.core.publisher.Mono
import javax.inject.Singleton

@Singleton
class GuestPreferenceRepository(
    private val guestPreferenceDAO: GuestPreferenceDAO,
    private val retryableStatementExecutor: RetryableStatementExecutor
) {
    private val className = GuestPreferenceRepository::class.java.name

    fun saveGuestPreference(guestPreferenceEntity: GuestPreferenceEntity): Mono<GuestPreferenceEntity> {
        return retryableStatementExecutor.write(className, "saveGuestPreference") { consistency ->
            guestPreferenceDAO.saveGuestPreference(guestPreferenceEntity, consistency) }
                .map { guestPreferenceEntity }
    }

    fun findGuestPreference(guestId: GuestId): Mono<GuestPreferenceEntity> {
        return retryableStatementExecutor.read(className, "findGuestPreference") { consistency ->
            guestPreferenceDAO.findGuestPreference(guestId, consistency) }
    }
}