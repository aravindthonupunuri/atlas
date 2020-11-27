package com.tgt.lists.atlas.api.purge.event.handler

import com.tgt.lists.atlas.kafka.model.UpdateListNotifyEvent
import com.tgt.lists.atlas.api.purge.service.PurgeUpdateListService
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
class PurgeUpdateListNotifyEventHandler(
    @Inject private val purgeUpdateListService: PurgeUpdateListService,
    @Inject private val eventHeaderFactory: EventHeaderFactory
) {
    private val logger = KotlinLogging.logger {}

    fun handlePurgeUpdateListNotifyEvent(
        updateListNotifyEvent: UpdateListNotifyEvent,
        eventHeaders: EventHeaders,
        isPoisonEvent: Boolean
    ): Mono<EventProcessingResult> {
        val processingState: PurgeUpdateListService.RetryState = if (updateListNotifyEvent.retryState != null) {
            PurgeUpdateListService.RetryState.deserialize(updateListNotifyEvent.retryState.toString())
        } else {
            PurgeUpdateListService.RetryState()
        }

        return purgeUpdateListService.updateListExpirationDate(updateListNotifyEvent.guestId,
            updateListNotifyEvent.listId, updateListNotifyEvent.expiration!!, processingState)
            .map {
                if (it.completeState()) {
                    logger.debug("updateListNotifyEvent processing is complete")
                    EventProcessingResult(true, eventHeaders, updateListNotifyEvent)
                } else {
                    logger.debug("updateListNotifyEvent didn't complete, adding it to DLQ for retry")
                    val message = "Error from handleUpdateListNotifyEvent() for guest: " +
                        "${updateListNotifyEvent.guestId} with listId: ${updateListNotifyEvent.listId}"
                    val retryHeader = eventHeaderFactory.nextRetryHeaders(eventHeaders = eventHeaders,
                        errorCode = 500, errorMsg = message)
                    updateListNotifyEvent.retryState = PurgeUpdateListService.RetryState.serialize(it)
                    EventProcessingResult(false, retryHeader, updateListNotifyEvent)
                }
            }
    }
}
