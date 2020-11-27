package com.tgt.lists.atlas.api.purge.event

import com.tgt.cronbeacon.kafka.model.CronEvent
import com.tgt.lists.atlas.kafka.model.CreateListNotifyEvent
import com.tgt.lists.atlas.kafka.model.UpdateListNotifyEvent
import com.tgt.lists.atlas.api.purge.event.handler.PurgeCreateListNotifyEventHandler
import com.tgt.lists.atlas.api.purge.event.handler.PurgeCronEventHandler
import com.tgt.lists.atlas.api.purge.event.handler.PurgeUpdateListNotifyEventHandler
import com.tgt.lists.msgbus.EventDispatcher
import com.tgt.lists.msgbus.event.DeadEventTransformedValue
import com.tgt.lists.msgbus.event.EventHeaders
import com.tgt.lists.msgbus.event.EventProcessingResult
import com.tgt.lists.msgbus.event.EventTransformedValue
import com.tgt.lists.msgbus.execution.ExecutionSerialization
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import mu.KotlinLogging
import reactor.core.publisher.Mono
import javax.inject.Inject
import javax.inject.Singleton

@Requires(property = "beacon.client.enabled", value = "true")
@Singleton
open class PurgeEventDispatcher(
    @Inject val purgeCreateListNotifyEventHandler: PurgeCreateListNotifyEventHandler,
    @Inject val purgeUpdateListNotifyEventHandler: PurgeUpdateListNotifyEventHandler,
    @Inject val purgeCronEventHandler: PurgeCronEventHandler,
    @Value("\${msgbus.source}") val source: String,
    @Value("\${msgbus.dlq-source}") val dlqSource: String,
    @Value("\${kafka-sources.allow}") val allowedSources: Set<String>
) : EventDispatcher {

    private val logger = KotlinLogging.logger {}

    override fun dispatchEvent(eventHeaders: EventHeaders, data: Any, isPoisonEvent: Boolean): Mono<EventProcessingResult> {
        if (eventHeaders.source == source || eventHeaders.source == dlqSource || allowedSources.contains(eventHeaders.source)) {
            // handle following events only from configured source
            when (eventHeaders.eventType) {
                CreateListNotifyEvent.getEventType() -> {
                    // always use transformValue to convert raw data to concrete type
                    val createListNotifyEvent = data as CreateListNotifyEvent
                    logger.debug { "Got CreateListNotifyEvent: $createListNotifyEvent" }
                    return purgeCreateListNotifyEventHandler.handlePurgeCreateListNotifyEvent(createListNotifyEvent, eventHeaders, isPoisonEvent)
                }
                UpdateListNotifyEvent.getEventType() -> {
                    // always use transformValue to convert raw data to concrete type
                    val updateListNotifyEvent = data as UpdateListNotifyEvent
                    logger.debug { "Got UpdateListNotifyEvent: $updateListNotifyEvent" }
                    return purgeUpdateListNotifyEventHandler.handlePurgeUpdateListNotifyEvent(updateListNotifyEvent, eventHeaders, isPoisonEvent)
                }
                CronEvent.getEventType() -> {
                    // always use transformValue to convert raw data to concrete type
                    val cronEvent = data as CronEvent
                    logger.debug { "Got cron Event: $cronEvent" }
                    return purgeCronEventHandler.handlePurgeCronEvent(cronEvent, eventHeaders, isPoisonEvent)
                }
            }
        }

        logger.debug { "Unhandled eventType: ${eventHeaders.eventType}" }
        return Mono.just(EventProcessingResult(true, eventHeaders, data))
    }

    override fun transformValue(eventHeaders: EventHeaders, data: ByteArray): EventTransformedValue? {
        if (eventHeaders.source == source || eventHeaders.source == dlqSource || allowedSources.contains(eventHeaders.source)) {
            return when (eventHeaders.eventType) {
                CreateListNotifyEvent.getEventType() -> {
                    val createListNotifyEvent = CreateListNotifyEvent.deserialize(data)
                    EventTransformedValue("lists_${createListNotifyEvent.listId}", ExecutionSerialization.ID_SERIALIZATION, createListNotifyEvent)
                }
                UpdateListNotifyEvent.getEventType() -> {
                    val updateListNotifyEvent = UpdateListNotifyEvent.deserialize(data)
                    EventTransformedValue("lists_${updateListNotifyEvent.listId}", ExecutionSerialization.ID_SERIALIZATION, updateListNotifyEvent)
                }
                CronEvent.getEventType() -> {
                    val cronEvent = CronEvent.deserialize(data)
                    EventTransformedValue("cron_${cronEvent.eventDateTime}", ExecutionSerialization.ID_SERIALIZATION, cronEvent)
                }
                CronEvent.getEventType() -> {
                    val cronEvent = CronEvent.deserialize(data)
                    if (cronEvent.target == "*" || cronEvent.target == source) {
                        EventTransformedValue("cron_${cronEvent.eventDateTime}", ExecutionSerialization.ID_SERIALIZATION, cronEvent)
                    } else {
                        null
                    }
                }
                else -> null
            }
        }

        return null
    }

    override fun handleDlqDeadEvent(eventHeaders: EventHeaders, data: ByteArray): DeadEventTransformedValue? {
        return null
    }
}
