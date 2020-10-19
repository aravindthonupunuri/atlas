package com.tgt.lists.atlas.api.persistence.internal

import com.tgt.lists.atlas.api.domain.model.GuestPreference
import com.tgt.lists.atlas.api.persistence.GuestPreferenceRepository
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.reactive.ReactiveStreamsCrudRepository

/**
 * GuestPreferenceRepositoryInternal is meant to be internally used by decorator GuestPreferenceRepository only.
 * It should never be injected directly in other application classes.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
interface GuestPreferenceCrudRepository : ReactiveStreamsCrudRepository<GuestPreference, String>, GuestPreferenceRepository
