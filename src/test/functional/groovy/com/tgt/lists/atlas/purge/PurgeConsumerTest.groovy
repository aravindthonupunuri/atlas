package com.tgt.lists.atlas.purge

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.cronbeacon.kafka.model.CronEvent
import com.tgt.lists.atlas.BaseKafkaFunctionalTest
import com.tgt.lists.atlas.PreDispatchLambda
import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.purge.persistence.cassandra.PurgeRepository
import com.tgt.lists.atlas.purge.persistence.entity.PurgeEntity
import com.tgt.lists.atlas.util.ListDataProvider
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
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import javax.inject.Inject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.stream.Collectors

@MicronautTest
@Stepwise
class PurgeConsumerTest extends BaseKafkaFunctionalTest {

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
    @Inject
    ListRepository listsRepository
    String timeZone
    ZoneId timeZoneId

    @Shared ListDataProvider dataProvider = new ListDataProvider()
    @Shared List<UUID> listIds = [Uuids.timeBased(), Uuids.timeBased(), Uuids.timeBased(), Uuids.timeBased(), Uuids.timeBased(), Uuids.timeBased()]
    @Shared List<LocalDate> localDateList = [LocalDate.now(), LocalDate.of(2100, 05, 02)]



    def setupSpec() {
        testEventListener = new TestEventListener()
        testEventListener.tracer = tracer
        eventNotificationsProvider.registerListener(testEventListener)
    }

    def setup() {
        testEventListener.reset()
        timeZone = "America/Chicago"
        timeZoneId = ZoneId.of(timeZone)
    }

    @Unroll
    def "test add lists expiration"() {
        given:
        PurgeEntity purgeEntity = dataProvider.createPurgeEntity(listId, bucket, expiration)

        when:
        purgeRepository.savePurgeExpiration(purgeEntity).block()

        then:
        notThrown(Throwable)

        where:
        listId       | bucket  | expiration
        listIds[0]   | 0       | localDateList[0]
        listIds[1]   | 1       | localDateList[0]
        listIds[2]   | 2       | localDateList[0]
        listIds[0]   | 1       | localDateList[1]

    }

    @Unroll
    def "test create new list #listId"() {
        given:
        ListEntity listEntity = dataProvider.createListEntityWithExpiration(listId, "title1", "shopping",  "s", Uuids.timeBased().toString(), "d", expiration )

        when:
        listsRepository.saveList(listEntity).block()

        then:
        notThrown(Throwable)

        where:
        listId       | expiration
        listIds[0]   | localDateList[1]
        listIds[1]   | localDateList[0]
        listIds[2]   | localDateList[0]
    }

    def "Test Purge Create List Notify Event"() {
        given:
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)
        CronEvent event = dataProvider.createCronEvent(LocalDateTime.now(), 2, 1L, 5, timeZoneId)

        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(String topic, @NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                if (eventHeaders.eventType == CronEvent.getEventType()) {
                    def cronEvent = CronEvent.deserialize(data)
                    if (cronEvent.minuteBlockOfHour == event.minuteBlockOfHour) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        listMessageBusClient.sendMessage(Uuids.timeBased().toString(), Uuids.timeBased(), CronEvent.getEventType(), "cronbeacon", event)

        then:
        testEventListener.verifyEvents { consumerEvents, producerEvents, consumerStatusEvents ->
            conditions.eventually {
                TestEventListener.Result[] completedEvents = consumerEvents.stream().filter {
                    def result = (TestEventListener.Result) it
                    (!result.preDispatch)
                }.collect(Collectors.toList())
                assert completedEvents.any { it.eventHeaders.eventType == CronEvent.getEventType() && it.success }
            }
        }
    }

    def "test get lists by ids"() {
        when:
        List<ListEntity> listEntity  = listsRepository.findLists([listIds[0], listIds[1], listIds[2]] as Set).collectList().block()

        then:
        listEntity.size() == 1
        listEntity.first().id == listIds[0]
    }

    @KafkaClient(acks = KafkaClient.Acknowledge.ALL, id = "cronbeacon-client")
    static interface ListMessageBusClient {
        @Topic("cronbeacon")
        String sendMessage(@KafkaKey String id, @Header UUID uuid, @Header String event_type, @Header String source,
                           @Body Object object)
    }
}
