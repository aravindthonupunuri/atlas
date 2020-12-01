package com.tgt.lists.atlas.api.domain

import com.tgt.lists.atlas.api.type.EventType
import com.tgt.lists.atlas.api.util.TestListEvaluator
import com.tgt.lists.msgbus.ListsDlqProducer
import com.tgt.lists.msgbus.ListsMessageBusProducer
import com.tgt.lists.msgbus.event.EventHeaderFactory
import org.apache.kafka.clients.producer.RecordMetadata
import reactor.core.publisher.Mono
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventPublisher(
    @Inject private val listsMessageBusProducer: ListsMessageBusProducer<String, Any>,
    @Inject private val listsDLQProducer: ListsDlqProducer<String, Any>,
    @Inject private val eventHeaderFactory: EventHeaderFactory
) {

    fun publishEvent(eventType: EventType, message: Any, partitionKey: String): Mono<RecordMetadata> {
        return listsMessageBusProducer.sendMessage(eventType, message, partitionKey, TestListEvaluator.evaluate())
                .onErrorResume { handleMsgbusProducerError(eventType, message, partitionKey) }
    }

    private fun handleMsgbusProducerError(
        eventType: EventType,
        message: Any,
        partitionKey: String
    ): Mono<RecordMetadata> {
        // Exception publishing item completion kafka event, so sending it to DLQ topic for retry
        val headers = eventHeaderFactory.nextRetryHeaders(
                eventHeaders = eventHeaderFactory.newEventHeaders(eventType = eventType, testMode = TestListEvaluator.evaluate()),
                errorCode = 500,
                errorMsg = "Failure publishing item completion kafka event with message with message $message " +
                        "to message bus kafka topic, so publishing it to DLQ topic")
        return listsDLQProducer.sendMessage(headers, message, partitionKey)
    }
}
