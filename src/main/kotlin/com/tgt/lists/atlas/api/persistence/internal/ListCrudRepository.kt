package com.tgt.lists.atlas.api.persistence.internal

import com.tgt.lists.atlas.api.domain.model.List
import com.tgt.lists.atlas.api.persistence.ListRepository
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.reactive.ReactiveStreamsCrudRepository
import java.util.*

/**
 * ListRepositoryInternal is meant to be internally used by decorator ListRepository only.
 * It should never be injected directly in other application classes.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
interface ListCrudRepository : ReactiveStreamsCrudRepository<List, UUID>, ListRepository
