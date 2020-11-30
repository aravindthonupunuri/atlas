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

    fun purgeStaleLists(retryState: RetryState): Mono<RetryState> {
        return if (retryState.incompleteState()) {
            val currentDate = LocalDate.now()
            purgeRepository.findPurgeExpiration(currentDate).collectList()
                    .flatMap {
                        Flux.fromIterable(it.asIterable()).flatMap { purgeEntity ->
                            listRepository.findListById(purgeEntity.listId!!)
                                    .flatMap {
                                        if (it.expiration == currentDate) {
                                            deleteListService.deleteList(it.guestId!!, it.id!!).map { true }
                                        } else {
                                            logger.debug("From purgeStaleLists(), Skipping deletion of list ${purgeEntity.listId}, with expiration ${it.expiration}")
                                            Mono.just(true)
                                        }
                                    }
                                    .switchIfEmpty {
                                        logger.debug("From purgeStaleLists(), List not found: ${purgeEntity.listId}")
                                        Mono.just(true)
                                    }
                        }.then(Mono.just(true)) }
                    .map {
                        retryState.purgeStaleLists = true
                        retryState
                    }
                    .onErrorResume {
                        logger.error("Exception from purgeStaleLists()", it)
                        Mono.just(retryState)
                    }
        } else {
            logger.debug("From purgeStaleLists(), processing complete")
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
