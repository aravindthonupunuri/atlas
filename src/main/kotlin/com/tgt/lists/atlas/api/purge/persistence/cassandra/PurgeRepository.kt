package com.tgt.lists.atlas.api.purge.persistence.cassandra

import com.tgt.lists.atlas.api.purge.persistence.entity.PurgeEntity
import com.tgt.lists.atlas.api.purge.persistence.cassandra.internal.PurgeDAO
import com.tgt.lists.atlas.api.purge.service.Buckets
import com.tgt.lists.micronaut.cassandra.RetryableStatementExecutor
import io.micronaut.context.annotation.Requires
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.time.LocalDate
import java.util.*
import javax.inject.Singleton

@Requires(property = "beacon.client.enabled", value = "true")
@Singleton
class PurgeRepository(
    private val purgeDao: PurgeDAO,
    private val retryableStatementExecutor: RetryableStatementExecutor
) {

    private val className = PurgeRepository::class.java.name

    fun savePurgeExpiration(purgeEntity: PurgeEntity): Mono<PurgeEntity> {
        return retryableStatementExecutor.write(className, "savePurgeExpiration") { consistency ->
            purgeDao.savePurgeExpiration(purgeEntity, consistency) }
                .map { purgeEntity }
                .switchIfEmpty { Mono.just(purgeEntity) }
    }

    fun findPurgeExpiration(expiration: LocalDate): Flux<PurgeEntity> {
        return retryableStatementExecutor.readFlux(className, "findPurgeExpiration") { consistency ->
            purgeDao.findPurgeExpiration(expiration, Buckets.getBuckets(), consistency) }
    }

    fun findPurgeExpirationByListId(expiration: LocalDate, listId: UUID): Mono<PurgeEntity> {
        return retryableStatementExecutor.read(className, "findPurgeExpirationByListId") { consistency ->
            purgeDao.findPurgeExpirationByListId(expiration, Buckets.getBuckets(), listId, consistency) }
    }
}