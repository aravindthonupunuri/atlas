package com.tgt.lists.atlas.purge.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tgt.lists.atlas.purge.persistence.entity.PurgeEntity
import com.tgt.lists.atlas.purge.persistence.cassandra.PurgeRepository
import com.tgt.lists.atlas.purge.util.Buckets
import io.micronaut.context.annotation.Requires
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Requires(property = "beacon.client.enabled", value = "true")
@Singleton
class PurgeCreateListService(
    @Inject private val purgeRepository: PurgeRepository
) {
    private val logger = KotlinLogging.logger { PurgeCreateListService::class.java.name }

    fun saveListExpirationDate(
        guestId: String,
        listId: UUID,
        expiration: LocalDate,
        retryState: RetryState
    ): Mono<RetryState> {
        return if (retryState.incompleteState()) {
            logger.debug("From saveListExpirationDate(), starting processing")
            purgeRepository.savePurgeExpiration(PurgeEntity(expiration, Buckets.getBucket(LocalDateTime.now()), listId))
                    .map {
                        retryState.savePurgeExpiration = true
                        retryState
                    }
                    .onErrorResume {
                        logger.error("Exception from saveListExpirationDate()", it)
                        Mono.just(retryState)
                    }
        } else {
            logger.debug("From saveListExpirationDate(), processing complete")
            Mono.just(retryState)
        }
    }

    data class RetryState(
        var savePurgeExpiration: Boolean = false
    ) {
        fun completeState(): Boolean {
            return savePurgeExpiration
        }

        fun incompleteState(): Boolean {
            return !savePurgeExpiration
        }

        companion object {
            // jacksonObjectMapper() returns a normal ObjectMapper with the KotlinModule registered
            val jsonMapper: ObjectMapper = jacksonObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)

            @JvmStatic
            fun deserialize(retryState: String): RetryState {
                return jsonMapper.readValue<RetryState>(retryState, RetryState::class.java)
            }

            @JvmStatic
            fun serialize(retryState: RetryState): String {
                return jsonMapper.writeValueAsString(retryState)
            }
        }
    }
}
