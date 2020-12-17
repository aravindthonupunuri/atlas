package com.tgt.lists.atlas.api.persistence.cassandra

import com.tgt.lists.atlas.api.domain.model.entity.ListPreferenceEntity
import com.tgt.lists.atlas.api.persistence.cassandra.internal.ListPreferenceDAO
import com.tgt.lists.atlas.api.util.Constants.DEFAULT_GUEST_ID
import com.tgt.lists.micronaut.cassandra.RetryableStatementExecutor
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.util.*
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class ListPreferenceRepository(
    @Named("ListPreferenceInstrumentedDAO") private val listPreferenceDAO: ListPreferenceDAO,
    private val retryableStatementExecutor: RetryableStatementExecutor
) {

    private val className = ListPreferenceRepository::class.java.name

    fun saveListPreference(listPreferenceEntity: ListPreferenceEntity): Mono<ListPreferenceEntity> {
        // GuestId is just a placeholder, we use Default Guest ID to save item sort order regardless of who adds/sorts an item
        // Once collaboration feature kicks in we can always revisit the logic
        return retryableStatementExecutor.write(className, "saveListPreference") { consistency ->
            listPreferenceDAO.saveListPreference(listPreferenceEntity.copy(guestId = DEFAULT_GUEST_ID), consistency) }
                .map { listPreferenceEntity }
                .switchIfEmpty { Mono.just(listPreferenceEntity) }
    }

    fun getListPreference(listId: UUID, guestId: String): Mono<ListPreferenceEntity> {
        // GuestId is just a placeholder, we use Default Guest ID to save item sort order regardless of who adds/sorts an item
        // Once collaboration feature kicks in we can always revisit the logic
        return retryableStatementExecutor.read(className, "findListPreferenceByListAndGuestId") { consistency ->
            listPreferenceDAO.findListPreferenceByListAndGuestId(listId, DEFAULT_GUEST_ID, consistency) }
                .map { it.copy(guestId = guestId) } // Replace Default Guest id with requested guestId
    }

    fun deleteListPreferenceByListAndGuestId(listPreferenceEntity: ListPreferenceEntity): Mono<ListPreferenceEntity> {
        // GuestId is just a placeholder, we use Default Guest ID to save item sort order regardless of who adds/sorts an item
        // Once collaboration feature kicks in we can always revisit the logic
        return retryableStatementExecutor.write(className, "deleteListPreferenceByListAndGuestId") { consistency ->
            listPreferenceDAO.deleteListPreferenceByListAndGuestId(listPreferenceEntity.copy(guestId = DEFAULT_GUEST_ID), consistency) }
                .map { listPreferenceEntity }
                .switchIfEmpty { Mono.just(listPreferenceEntity) }
    }
}