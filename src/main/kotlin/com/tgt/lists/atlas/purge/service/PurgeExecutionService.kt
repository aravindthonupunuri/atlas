package com.tgt.lists.atlas.purge.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.service.DeleteListService
import com.tgt.lists.atlas.purge.persistence.cassandra.PurgeRepository
import io.micronaut.context.annotation.Requires
import mu.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Requires(property = "beacon.client.enabled", value = "true")
@Singleton
class PurgeExecutionService(
    @Inject private val purgeRepository: PurgeRepository,
    @Inject private val listRepository: ListRepository,
    @Inject private val deleteListService: DeleteListService
) {
    private val logger = KotlinLogging.logger { PurgeExecutionService::class.java.name }

    fun purgeStaleLists(retryState: RetryState, purgeDate: LocalDate): Mono<RetryState> {
        return if (retryState.incompleteState()) {
            logger.info("purgeStaleLists: Executing with incompleteState and purgeDate=$purgeDate")
            purgeRepository.findPurgeExpiration(purgeDate).collectList()
                    .flatMap { purgeEntities ->
                        if (purgeEntities.size == 0) {
                            logger.info("purgeStaleLists: No Lists were found for purgeDate: $purgeDate")
                        }
                        Flux.fromIterable(purgeEntities.asIterable()).flatMap { purgeEntity ->
                            logger.debug("purgeStaleLists: processing listId: ${purgeEntity.listId!!}")
                            listRepository.findListById(purgeEntity.listId!!)
                                    .flatMap { listEntity ->
                                        if (listEntity.expiration == purgeDate) {
                                            logger.info("purgeStaleLists: Deleting listId: ${purgeEntity.listId!!}")
                                            deleteListService.deleteList(listEntity.guestId!!, listEntity.id!!).map { true }
                                        } else {
                                            logger.info("purgeStaleLists: Skipping deletion of listId ${purgeEntity.listId}, with expiration ${listEntity.expiration}")
                                            Mono.just(true)
                                        }
                                    }
                                    .switchIfEmpty {
                                        logger.info("purgeStaleLists: List not found: ${purgeEntity.listId}")
                                        Mono.just(true)
                                    }
                        }
                        .then(Mono.just(true))
                    }
                    .map {
                        logger.info("purgeStaleLists: processing is successfully completed")
                        retryState.purgeStaleLists = true
                        retryState
                    }
                    .onErrorResume {
                        logger.error("Exception from purgeStaleLists()", it)
                        Mono.just(retryState)
                    }
        } else {
            logger.info("purgeStaleLists: processing is already complete")
            Mono.just(retryState)
        }
    }

    data class RetryState(
        var purgeStaleLists: Boolean = false
    ) {
        fun completeState(): Boolean {
            return purgeStaleLists
        }

        fun incompleteState(): Boolean {
            return !purgeStaleLists
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
