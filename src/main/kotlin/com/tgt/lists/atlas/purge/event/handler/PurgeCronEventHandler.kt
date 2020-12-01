package com.tgt.lists.atlas.purge.event.handler

import com.tgt.cronbeacon.kafka.model.CronEvent
import com.tgt.lists.atlas.api.domain.Configuration
import com.tgt.lists.atlas.purge.service.PurgeExecutionService
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
class PurgeCronEventHandler(
    @Inject private val purgeExecutionService: PurgeExecutionService,
    @Inject private val eventHeaderFactory: EventHeaderFactory,
    @Inject private val configuration: Configuration
) {
    private val logger = KotlinLogging.logger {}

    private val hourOfDay = configuration.purgeExecutionHourOfDay
    private val minuteBlockOfHour = configuration.purgeExecutionMinuteBlockOfHour

    fun handlePurgeCronEvent(
        cronEvent: CronEvent,
        eventHeaders: EventHeaders,
        isPoisonEvent: Boolean
    ): Mono<EventProcessingResult> {
        val processingState: PurgeExecutionService.RetryState = if (cronEvent.retryState != null) {
            PurgeExecutionService.RetryState.deserialize(cronEvent.retryState.toString())
        } else {
            PurgeExecutionService.RetryState()
        }

        return if (cronEvent.hourOfDay == hourOfDay && cronEvent.minuteBlockOfHour == minuteBlockOfHour) {
            purgeExecutionService.purgeStaleLists(processingState).map {
                if (it.completeState()) {
                    logger.debug("PurgeStaleLists processing is complete")
                    EventProcessingResult(true, eventHeaders, cronEvent)
                } else {
                    logger.debug("PurgeStaleLists didn't complete, adding it to DLQ for retry")
                    val message = "Error from handlePurgeCronEvent() for cronEvent with hourOfDay: ${cronEvent.hourOfDay} and minuteBlockOfHour: ${cronEvent.minuteBlockOfHour}"
                    val retryHeader = eventHeaderFactory.nextRetryHeaders(eventHeaders = eventHeaders, errorCode = 500, errorMsg = message)
                    cronEvent.retryState = PurgeExecutionService.RetryState.serialize(it)
                    EventProcessingResult(false, retryHeader, cronEvent)
                }
            }
        } else {
            logger.debug("Ignoring cronEvent with hourOfDay: ${cronEvent.hourOfDay} and minuteBlockOfHour: ${cronEvent.minuteBlockOfHour}")
            Mono.just(EventProcessingResult(true, eventHeaders, cronEvent))
        }
    }
}
