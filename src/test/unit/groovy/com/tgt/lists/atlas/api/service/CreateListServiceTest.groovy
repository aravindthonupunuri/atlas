package com.tgt.lists.atlas.api.service


import com.tgt.lists.atlas.api.domain.DefaultListManager
import com.tgt.lists.atlas.api.domain.EventPublisher
import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.ListRequestTO
import com.tgt.lists.atlas.api.util.Constants
import com.tgt.lists.atlas.kafka.model.CreateListNotifyEvent
import com.tgt.lists.atlas.util.TestListChannel
import org.apache.kafka.clients.producer.RecordMetadata
import reactor.core.publisher.Mono
import spock.lang.Specification

class CreateListServiceTest extends Specification {

    ListRepository listRepository
    EventPublisher eventPublisher
    CreateListService createListService
    DefaultListManager defaultListManager
    String guestId = "1234"
    Long abandonAfterDurationInDays = 730
    String listType = "SHOPPING"

    def setup() {
        listRepository = Mock(ListRepository)
        eventPublisher = Mock(EventPublisher)
        defaultListManager = Mock(DefaultListManager)
        createListService = new CreateListService(listRepository, defaultListManager, eventPublisher, abandonAfterDurationInDays, false)
        createListService.listType = listType
    }

    def "Test createList() integrity"() {
        given:
        def title = "list1"
        def channel = TestListChannel.WEB.toString()
        def desc = "my favorite list"
        def listRequest = new ListRequestTO(channel, title,"fav",  Long.valueOf(Constants.LIST_DEFAULT_LOCATION_ID), desc, true, null, null)
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
        actual.defaultList
    }

    def "Test create default list for guest having preexisting default list"() {
        def title = "list1"
        def channel = TestListChannel.WEB.toString()
        def desc = "my favorite list"
        def listRequest = new ListRequestTO(channel, title, "fav", Long.valueOf(Constants.LIST_DEFAULT_LOCATION_ID), desc, true, null, null)
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

