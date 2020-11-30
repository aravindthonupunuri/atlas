package com.tgt.lists.atlas.purge.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tgt.lists.atlas.purge.persistence.cassandra.PurgeRepository
import com.tgt.lists.atlas.purge.persistence.entity.PurgeEntity
import com.tgt.lists.atlas.purge.util.Buckets
import io.micronaut.context.annotation.Requires
import mu.KotlinLogging
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Requires(property = "beacon.client.enabled", value = "true")
@Singleton
class PurgeUpdateListService(
    @Inject private val purgeRepository: PurgeRepository
) {
    private val logger = KotlinLogging.logger { PurgeUpdateListService::class.java.name }

    fun updateListExpirationDate(
        guestId: String,
        listId: UUID,
        expiration: LocalDate,
        retryState: RetryState
    ): Mono<RetryState> {
        return if (retryState.incompleteState()) {
            logger.debug("From updateListExpirationDate(), starting processing")
            purgeRepository.findPurgeExpirationByListId(expiration, listId)
                    .flatMap {
                        logger.debug("From updateListExpirationDate(), Pre existing list: ${it.listId} and same expiration date: ${it.expiration}, so skipping the update process")
                        retryState.updateListExpiration = true
                        Mono.just(retryState)
                    }
                    .switchIfEmpty {
                        val bucket = Buckets.getBucket(LocalDateTime.now())
                        purgeRepository.savePurgeExpiration(PurgeEntity(expiration, bucket, listId)).map {
                            logger.debug("From updateListExpirationDate(), Updated list: ${it.listId} with expiration date: ${it.expiration} and bucket: ${it.bucket}")
                            retryState.updateListExpiration = true
                            retryState
                        }
                    }
                    .onErrorResume {
                        logger.error("Exception from updateListExpirationDate()", it)
                        Mono.just(retryState)
                    }
        } else {
            logger.debug("From updateListExpirationDate(), processing complete")
            Mono.just(retryState)
        }
    }

    data class RetryState(
        var updateListExpiration: Boolean = false
    ) {
        fun completeState(): Boolean {
            return updateListExpiration
        }

        fun incompleteState(): Boolean {
            return !updateListExpiration
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
