package com.tgt.lists.atlas.api.service

import com.datastax.driver.core.utils.UUIDs
import com.tgt.lists.atlas.api.domain.DefaultListManager
import com.tgt.lists.atlas.api.domain.EventPublisher
import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.ListRequestTO
import com.tgt.lists.atlas.api.type.LIST_STATE
import com.tgt.lists.atlas.api.type.UserMetaData
import com.tgt.lists.atlas.api.util.Constants
import com.tgt.lists.atlas.api.util.DateUtilKt
import com.tgt.lists.atlas.kafka.model.CreateListNotifyEvent
import com.tgt.lists.atlas.util.ListDataProvider
import com.tgt.lists.atlas.util.TestListChannel
import org.apache.kafka.clients.producer.RecordMetadata
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate

class CreateListServiceTest extends Specification {

    ListRepository listRepository
    EventPublisher eventPublisher
    CreateListService createListService
    DefaultListManager defaultListManager
    ListDataProvider listDataProvider
    String guestId = "1234"
    String listType = "SHOPPING"

    def setup() {
        listRepository = Mock(ListRepository)
        eventPublisher = Mock(EventPublisher)
        defaultListManager = Mock(DefaultListManager)
        listDataProvider = new ListDataProvider()
        createListService = new CreateListService(listRepository, defaultListManager, eventPublisher, listDataProvider.getConfiguration(3, 5, 5, true, false, false))
    }

    def "Test createList() integrity"() {
        given:
        def title = "list1"
        def channel = TestListChannel.WEB.toString()
        def desc = "my favorite list"
        Map metadata = [
                "structure": [
                        "doors": [[
                                          "id": "1234",
                                          "modelURL": "test-url",
                                          "position": ["x": 40, "y": 50, "z": 60],
                                          "rotation": ["x": 30, "y": 20, "z": 10]
                                  ]],
                        "windows": [[
                                            "id": "12345",
                                            "modelURL": "window model url",
                                            "position": ["x": 10, "y": 15, "z": 20],
                                            "rotation": ["x": 25, "y": 30, "z": 35]
                                    ]],
                        "wallHeight": 12,
                        "wallDepth": 12
                ]
        ]
        def listRequest = new ListRequestTO(channel, title,"fav", LIST_STATE.ACTIVE, LocalDate.of(2100,9,12), null, Long.valueOf(Constants.LIST_DEFAULT_LOCATION_ID), desc, true, null, new UserMetaData(metadata))
        def recordMetadata = GroovyMock(RecordMetadata)

        when:
        def actual = createListService.createList(guestId, listRequest).block()

        then:
        1 * defaultListManager.processDefaultListInd(*_) >> Mono.just(true)
        1 * listRepository.saveList(_ as ListEntity) >> { ListEntity listEntity -> return Mono.just(listEntity) }
        1 * eventPublisher.publishEvent(CreateListNotifyEvent.getEventType(), _ as CreateListNotifyEvent, guestId) >>  Mono.just(recordMetadata)

        actual.listId != null
        actual.channel == channel
        actual.listTitle == title
        actual.shortDescription == desc
        actual.listType == listType
        actual.expiration == listRequest.expiration
        actual.defaultList
    }

    def "Test createList() integrity for migration"() {
        given:
        def title = "list1"
        def channel = TestListChannel.WEB.toString()
        def desc = "my favorite list"
        def legacyListId = UUIDs.timeBased()
        def legacyListCreatedAt = Instant.now()
        Map metadata = [
                "structure": [
                        "doors": [[
                                          "id": "1234",
                                          "modelURL": "test-url",
                                          "position": ["x": 40, "y": 50, "z": 60],
                                          "rotation": ["x": 30, "y": 20, "z": 10]
                                  ]],
                        "windows": [[
                                            "id": "12345",
                                            "modelURL": "window model url",
                                            "position": ["x": 10, "y": 15, "z": 20],
                                            "rotation": ["x": 25, "y": 30, "z": 35]
                                    ]],
                        "wallHeight": 12,
                        "wallDepth": 12
                ]
        ]
        def listRequest = new ListRequestTO(channel, title,"fav", LIST_STATE.ACTIVE, LocalDate.of(2100,9,12), null, Long.valueOf(Constants.LIST_DEFAULT_LOCATION_ID), desc, true, null, new UserMetaData(metadata))
        def recordMetadata = GroovyMock(RecordMetadata)

        when:
        def actual = createListService.createList(guestId, listRequest, legacyListId, legacyListCreatedAt, null).block()

        then:
        1 * defaultListManager.processDefaultListInd(*_) >> Mono.just(true)
        1 * listRepository.saveList(_ as ListEntity) >> { ListEntity listEntity -> return Mono.just(listEntity) }
        1 * eventPublisher.publishEvent(CreateListNotifyEvent.getEventType(), _ as CreateListNotifyEvent, guestId) >>  Mono.just(recordMetadata)

        actual.listId == legacyListId
        actual.channel == channel
        actual.listTitle == title
        actual.shortDescription == desc
        actual.listType == listType
        actual.expiration == listRequest.expiration
        actual.defaultList
        actual.addedTs == DateUtilKt.getLocalDateTimeFromInstant(legacyListCreatedAt)
    }

    def "Test create default list for guest having preexisting default list"() {
        def title = "list1"
        def channel = TestListChannel.WEB.toString()
        def desc = "my favorite list"
        def listRequest = new ListRequestTO(channel, title, "fav", LIST_STATE.ACTIVE, LocalDate.now(), null, Long.valueOf(Constants.LIST_DEFAULT_LOCATION_ID), desc, true, null, null)
        def recordMetadata = GroovyMock(RecordMetadata)

        when:
        def actual = createListService.createList(guestId, listRequest).block()

        then:
        1 * defaultListManager.processDefaultListInd(*_) >> Mono.just(false)
        1 * listRepository.saveList(_ as ListEntity) >> { ListEntity listEntity -> return Mono.just(listEntity) }
        1 * eventPublisher.publishEvent(CreateListNotifyEvent.getEventType(), _ as CreateListNotifyEvent, guestId) >>  Mono.just(recordMetadata)

        actual.listId != null
        actual.channel == channel
        actual.listTitle == title
        actual.shortDescription == desc
        actual.listType == listType
        !actual.defaultList

    }
}

