package com.tgt.lists.atlas.purge.event.handler

import com.tgt.lists.atlas.kafka.model.CreateListNotifyEvent
import com.tgt.lists.atlas.purge.service.PurgeCreateListService
import com.tgt.lists.msgbus.event.EventHeaderFactory
import com.tgt.lists.msgbus.event.EventHeaders
import com.tgt.lists.msgbus.event.EventProcessingResult
import io.micronaut.context.annotation.Requires
import mu.KotlinLogging
import reactor.core.publisher.Mono
import javax.inject.Inject
import javax.inject.Singleton

@Requires(property = "beacon.client.enabled", value = "true")
@Singleton
class PurgeCreateListNotifyEventHandler(
    @Inject private val purgeCreateListService: PurgeCreateListService,
    @Inject private val eventHeaderFactory: EventHeaderFactory
) {
    private val logger = KotlinLogging.logger {}

    fun handlePurgeCreateListNotifyEvent(
        createListNotifyEvent: CreateListNotifyEvent,
        eventHeaders: EventHeaders,
        isPoisonEvent: Boolean
    ): Mono<EventProcessingResult> {
        val processingState: PurgeCreateListService.RetryState = if (createListNotifyEvent.retryState != null) {
            PurgeCreateListService.RetryState.deserialize(createListNotifyEvent.retryState.toString())
        } else {
            PurgeCreateListService.RetryState()
        }

        return purgeCreateListService.saveListExpirationDate(createListNotifyEvent.guestId,
            createListNotifyEvent.listId, createListNotifyEvent.expiration, processingState)
            .map {
                if (it.completeState()) {
                    logger.debug("createListNotifyEventService processing is complete")
                    EventProcessingResult(true, eventHeaders, createListNotifyEvent)
                } else {
                    logger.debug("createListNotifyEventService didn't complete, adding it to DLQ for retry")
                    val message = "Error from handleCreateListNotifyEvent() for guest: " +
                        "${createListNotifyEvent.guestId} with listId: ${createListNotifyEvent.listId}"
                    val retryHeader = eventHeaderFactory.nextRetryHeaders(eventHeaders = eventHeaders,
                        errorCode = 500, errorMsg = message)
                    createListNotifyEvent.retryState = PurgeCreateListService.RetryState.serialize(it)
                    EventProcessingResult(false, retryHeader, createListNotifyEvent)
                }
            }
    }
}
