package com.tgt.lists.atlas.purge

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.BaseKafkaFunctionalTest
import com.tgt.lists.atlas.PreDispatchLambda
import com.tgt.lists.atlas.purge.persistence.cassandra.PurgeRepository
import com.tgt.lists.atlas.purge.persistence.entity.PurgeEntity
import com.tgt.lists.atlas.api.type.LIST_STATE
import com.tgt.lists.atlas.kafka.model.CreateListNotifyEvent
import com.tgt.lists.atlas.kafka.model.UpdateListNotifyEvent
import com.tgt.lists.msgbus.event.EventHeaders
import com.tgt.lists.msgbus.event.EventLifecycleNotificationProvider
import io.micronaut.configuration.kafka.annotation.KafkaClient
import io.micronaut.configuration.kafka.annotation.KafkaKey
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.messaging.annotation.Body
import io.micronaut.messaging.annotation.Header
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.opentracing.Tracer
import org.jetbrains.annotations.NotNull
import spock.lang.Shared
import spock.lang.Stepwise
import spock.util.concurrent.PollingConditions

import javax.inject.Inject
import java.time.LocalDate
import java.util.stream.Collectors

@MicronautTest
@Stepwise
class PurgeNotifyEventsConsumerTest extends BaseKafkaFunctionalTest {

    @Shared
    @Inject
    Tracer tracer
    @Shared
    @Inject
    EventLifecycleNotificationProvider eventNotificationsProvider
    @Shared
    TestEventListener testEventListener
    @Inject
    ListMessageBusClient listMessageBusClient
    @Inject
    PurgeRepository purgeRepository
    @Shared def guestId = "1234"
    @Shared def listId = Uuids.timeBased()

    def setupSpec() {
        testEventListener = new TestEventListener()
        testEventListener.tracer = tracer
        eventNotificationsProvider.registerListener(testEventListener)
    }

    def setup() {
        testEventListener.reset()
    }

    def "Test Purge Create List Notify Event"() {
        given:
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)
        CreateListNotifyEvent event = new CreateListNotifyEvent(guestId, listId, "shopping", "s", "title", "WEB", "WEB", LIST_STATE.ACTIVE, null, LocalDate.of(2100, 03, 01), null, null,  null, null, guestId, null)

        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(String topic, @NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                if (eventHeaders.eventType == CreateListNotifyEvent.getEventType()) {
                    def createListNotifyEvent = CreateListNotifyEvent.deserialize(data)
                    if (createListNotifyEvent.guestId == guestId.toString()) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        listMessageBusClient.sendMessage(event.guestId, Uuids.timeBased(), CreateListNotifyEvent.getEventType(), "atlas", event)

        then:
        testEventListener.verifyEvents { consumerEvents, producerEvents, consumerStatusEvents ->
            conditions.eventually {
                TestEventListener.Result[] completedEvents = consumerEvents.stream().filter {
                    def result = (TestEventListener.Result) it
                    (!result.preDispatch)
                }.collect(Collectors.toList())
                assert completedEvents.any { it.eventHeaders.eventType == CreateListNotifyEvent.getEventType() && it.success }
            }
        }
    }


    def "test findPurgeExpirationByListId"() {
        when:
        PurgeEntity purgeEntityList = purgeRepository.findPurgeExpirationByListId(LocalDate.of(2100, 03, 01), listId).block()

        then:
        purgeEntityList.listId == listId
    }

    def "Test Purge Update List Notify Event with same expiration"() {
        given:
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)
        UpdateListNotifyEvent event = new UpdateListNotifyEvent(guestId, listId, "shopping", "s", "title", "WEB", "WEB", LIST_STATE.ACTIVE, null, LocalDate.of(2100, 03, 01), null, null,  null, null, guestId, null)

        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(String topic, @NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                if (eventHeaders.eventType == UpdateListNotifyEvent.getEventType()) {
                    def updateListNotifyEvent = UpdateListNotifyEvent.deserialize(data)
                    if (updateListNotifyEvent.guestId == guestId.toString()) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        listMessageBusClient.sendMessage(event.guestId, Uuids.timeBased(), UpdateListNotifyEvent.getEventType(), "atlas", event)

        then:
        testEventListener.verifyEvents { consumerEvents, producerEvents, consumerStatusEvents ->
            conditions.eventually {
                TestEventListener.Result[] completedEvents = consumerEvents.stream().filter {
                    def result = (TestEventListener.Result) it
                    (!result.preDispatch)
                }.collect(Collectors.toList())
                assert completedEvents.any { it.eventHeaders.eventType == UpdateListNotifyEvent.getEventType() && it.success }
            }
        }
    }

    def "test findPurgeExpirationByListId after update"() {
        when:
        PurgeEntity purgeEntityList = purgeRepository.findPurgeExpirationByListId(LocalDate.of(2100, 03, 01), listId).block()

        then:
        purgeEntityList.listId == listId
    }

    def "Test Purge Update List Notify Event with expiration in future"() {
        given:
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)
        UpdateListNotifyEvent event = new UpdateListNotifyEvent(guestId, listId, "shopping", "s", "title", "WEB", "WEB", LIST_STATE.ACTIVE, null, LocalDate.of(2100, 04, 02), null, null,  null, null,guestId, null)
        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(String topic, @NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                if (eventHeaders.eventType == UpdateListNotifyEvent.getEventType()) {
                    def updateListNotifyEvent = UpdateListNotifyEvent.deserialize(data)
                    if (updateListNotifyEvent.guestId == guestId.toString()) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        listMessageBusClient.sendMessage(event.guestId, Uuids.timeBased(), UpdateListNotifyEvent.getEventType(), "atlas", event)

        then:
        testEventListener.verifyEvents { consumerEvents, producerEvents, consumerStatusEvents ->
            conditions.eventually {
                TestEventListener.Result[] completedEvents = consumerEvents.stream().filter {
                    def result = (TestEventListener.Result) it
                    (!result.preDispatch)
                }.collect(Collectors.toList())
                assert completedEvents.any { it.eventHeaders.eventType == UpdateListNotifyEvent.getEventType() && it.success }
            }
        }
    }

    def "test findPurgeExpirationByListId after update in expiration"() {
        when:
        PurgeEntity purgeEntityList1 = purgeRepository.findPurgeExpirationByListId(LocalDate.of(2100, 03, 01), listId).block()

        then:
        purgeEntityList1.listId == listId

        and:

        when:
        PurgeEntity purgeEntityList2 = purgeRepository.findPurgeExpirationByListId(LocalDate.of(2100, 04, 02), listId).block()

        then:
        purgeEntityList2.listId == listId
    }

    @KafkaClient(acks = KafkaClient.Acknowledge.ALL, id = "msg-bus-client")
    static interface ListMessageBusClient {
        @Topic("lists-msg-bus")
        String sendMessage(@KafkaKey String id, @Header UUID uuid, @Header String event_type, @Header String source,
                           @Body Object object)
    }
}
