package com.tgt.lists.atlas.api.persistence.cassandra

import com.tgt.lists.atlas.api.domain.model.entity.ListPreferenceEntity
import com.tgt.lists.atlas.api.persistence.cassandra.internal.ListPreferenceDAO
import com.tgt.lists.atlas.api.util.Constants.DEFAULT_GUEST_ID
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Singleton

@Singleton
class ListPreferenceRepository(
    private val listPreferenceDAO: ListPreferenceDAO
) {
    fun saveListPreference(listPreferenceEntity: ListPreferenceEntity): Mono<ListPreferenceEntity> {
        // GuestId is just a placeholder, we use Default Guest ID to save item sort order regardless of who adds/sorts an item
        // Once collaboration feature kicks in we can always revisit the logic
        return Mono.from(listPreferenceDAO.saveListPreference(listPreferenceEntity.copy(guestId = DEFAULT_GUEST_ID)))
                .map { listPreferenceEntity }
    }

    fun getListPreference(listId: UUID, guestId: String): Mono<ListPreferenceEntity> {
        // GuestId is just a placeholder, we use Default Guest ID to save item sort order regardless of who adds/sorts an item
        // Once collaboration feature kicks in we can always revisit the logic
        return Mono.from(listPreferenceDAO.findListPreferenceByListAndGuestId(listId, DEFAULT_GUEST_ID))
                .map { it.copy(guestId = guestId) } // Replace Default Guest id with requested guestId
    }

    fun deleteListPreferenceByListAndGuestId(listPreferenceEntity: ListPreferenceEntity): Mono<ListPreferenceEntity> {
        // GuestId is just a placeholder, we use Default Guest ID to save item sort order regardless of who adds/sorts an item
        // Once collaboration feature kicks in we can always revisit the logic
        return Mono.from(listPreferenceDAO.deleteListPreferenceByListAndGuestId(listPreferenceEntity.copy(guestId = DEFAULT_GUEST_ID)))
                .map { listPreferenceEntity }
    }
}