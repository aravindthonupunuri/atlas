package com.tgt.lists.atlas.api.service

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.fasterxml.jackson.databind.ObjectMapper
import com.tgt.lists.atlas.api.domain.DefaultListManager
import com.tgt.lists.atlas.api.domain.EventPublisher
import com.tgt.lists.atlas.api.domain.UpdateListManager
import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.service.transform.list.UserMetaDataTransformationStep
import com.tgt.lists.atlas.api.transport.ListUpdateRequestTO
import com.tgt.lists.atlas.api.type.LIST_STATE
import com.tgt.lists.atlas.api.type.UserMetaData
import com.tgt.lists.atlas.kafka.model.UpdateListNotifyEvent
import com.tgt.lists.atlas.util.ListDataProvider
import com.tgt.lists.atlas.util.TestListChannel
import com.tgt.lists.common.components.exception.BadRequestException
import org.apache.kafka.clients.producer.RecordMetadata
import org.jetbrains.annotations.NotNull
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.LocalDate

class UpdateListServiceTest extends Specification {

    ListRepository listRepository
    EventPublisher eventPublisher
    UpdateListManager updateListManager
    UpdateListService updateListService
    DefaultListManager defaultListManager
    ListDataProvider listDataProvider
    String guestId = "1234"
    String listType = "SHOPPING"
    def mapper = new ObjectMapper()
    UserMetaDataTransformationStep defaultUserMetaDataTransformationStep

    def setup() {
        listRepository = Mock(ListRepository)
        eventPublisher = Mock(EventPublisher)
        defaultListManager = Mock(DefaultListManager)
        listDataProvider = new ListDataProvider()
        updateListManager = new UpdateListManager(listRepository, eventPublisher,listDataProvider.getConfiguration(3, 5, 5, true, false, false, false))
        updateListService = new UpdateListService(listRepository, defaultListManager, updateListManager)

        defaultUserMetaDataTransformationStep = new UserMetaDataTransformationStep() {
            @Override
            Mono<UserMetaData> execute(@NotNull UserMetaData userMetaData) {
                return Mono.just(userMetaData)
            }
        }
    }

    def "Test updateList() integrity"() {
        given:
        def title = "list title"
        def updateTitle = "title updated"
        def channel = TestListChannel.WEB.toString()
        def desc = "my favorite list"
        def updateDesc = "description updated"
        def listUpdateRequestTO = new ListUpdateRequestTO(updateTitle, updateDesc, true, null, null, LocalDate.of(2100,9,02), defaultUserMetaDataTransformationStep)

        UUID listId = Uuids.timeBased()

        ListEntity existing = new ListEntity(listId, title, listType, null, guestId, desc, channel, null, "D", null, null, LIST_STATE.ACTIVE.value, null, null, LocalDate.of(2100, 5, 02), null, null, null )
        ListEntity updated = new ListEntity(listId, updateTitle, listType, null, guestId, updateDesc, channel, null, "D", null, null, LIST_STATE.ACTIVE.value, null, null, LocalDate.of(2100,9,02), null, null, null )

        when:
        def actual = updateListService.updateList(guestId, listId, listUpdateRequestTO).block()

        then:
        1 * defaultListManager.processDefaultListInd(*_) >> Mono.just(true)
        1 * listRepository.findListById(_) >> Mono.just(existing)
        1 * listRepository.updateList(_ ,_ as ListEntity) >> { arguments ->
            final ListEntity updatedList = arguments[1]
            assert updatedList.description == updateDesc
            assert updatedList.title == updateTitle
            Mono.just(updated)
        }
        1 * eventPublisher.publishEvent(UpdateListNotifyEvent.eventType, _, _) >> Mono.just(GroovyMock(RecordMetadata))

        actual.listId != null
        actual.channel == channel
        actual.listTitle == listUpdateRequestTO.listTitle
        actual.shortDescription == listUpdateRequestTO.shortDescription
        actual.expiration == listUpdateRequestTO.expiration
        actual.defaultList
    }

    def "Test updateList() integrity with transformation pipeline"() {
        given:
        def title = "list title"
        def updateTitle = "title updated"
        def channel = TestListChannel.WEB.toString()
        def desc = "my favorite list"
        def updateDesc = "description updated"
        Map metadata = [
                "structure": [
                        "wallHeight": 12,
                        "wallDepth": 12
                ]
        ]
        Map updatedMetadata = [
                "structure": [
                        "wallHeight": 10,
                        "wallDepth": 10
                ]
        ]

        def listUpdateRequestTO = new ListUpdateRequestTO(updateTitle, updateDesc, true, null, new UserMetaData(updatedMetadata), LocalDate.of(2100,03,02), defaultUserMetaDataTransformationStep)

        UUID listId = Uuids.timeBased()

        ListEntity existing = new ListEntity(listId, title, listType, null, guestId, desc, channel, null, "D", null, null, LIST_STATE.ACTIVE.value, mapper.writeValueAsString(metadata), null, LocalDate.of(2100, 05, 02), null, null, null )
        ListEntity updated = new ListEntity(listId, updateTitle, listType, null, guestId, updateDesc, channel, null, "D", null, null, LIST_STATE.ACTIVE.value, mapper.writeValueAsString(updatedMetadata), null, LocalDate.of(2100, 05, 02), null, null, null )

        when:
        def actual = updateListService.updateList(guestId, listId, listUpdateRequestTO).block()

        then:
        1 * defaultListManager.processDefaultListInd(*_) >> Mono.just(true)
        1 * listRepository.findListById(_) >> Mono.just(existing)
        1 * listRepository.updateList(_ , _ as ListEntity) >> { arguments ->
            final ListEntity updatedList = arguments[1]
            assert updatedList.description == updateDesc
            assert updatedList.title == updateTitle
            Mono.just(updated)
        }
        1 * eventPublisher.publishEvent(UpdateListNotifyEvent.eventType, _, _) >> Mono.just(GroovyMock(RecordMetadata))

        actual.listId != null
        actual.channel == channel
        actual.listTitle == listUpdateRequestTO.listTitle
        actual.shortDescription == listUpdateRequestTO.shortDescription
        actual.defaultList
    }

    def "Test updateList() integrity with missing transformation pipeline"() {
        given:
        def updateTitle = "title updated"
        def updateDesc = "description updated"
        Map updatedMetadata = [
                "structure": [
                        "wallHeight": 10,
                        "wallDepth": 10
                ]
        ]

        when:
        new ListUpdateRequestTO(updateTitle, updateDesc, true, null, new UserMetaData(updatedMetadata), LocalDate.of(2100, 03, 02), null)

        then:
        thrown(IllegalArgumentException)
    }

    def "Test updateList() with false default List value"() {
        given:
        def ListUpdateRequestTO = new ListUpdateRequestTO("updatedTitle", null, false, null, null, LocalDate.of(2100,03,02), defaultUserMetaDataTransformationStep)
        UUID listId = Uuids.timeBased()

        when:
        updateListService.updateList(guestId, listId, ListUpdateRequestTO).block()

        then:
        thrown(BadRequestException)
    }
}
