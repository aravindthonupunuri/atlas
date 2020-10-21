package com.tgt.lists.atlas.api.service

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.api.domain.EventPublisher
import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.kafka.model.DeleteListNotifyEvent
import com.tgt.lists.atlas.util.ListDataProvider
import org.apache.kafka.clients.producer.RecordMetadata
import reactor.core.publisher.Mono
import spock.lang.Specification

class DeleteListServiceTest extends Specification {

    ListRepository listRepository
    EventPublisher eventPublisher
    DeleteListService deleteListService
    ListDataProvider listDataProvider
    String guestId = "1234"

    def setup() {
        listRepository = Mock(ListRepository)
        eventPublisher = Mock(EventPublisher)
        deleteListService = new DeleteListService(listRepository, eventPublisher)
        listDataProvider = new ListDataProvider()
    }

    def "Test deleteListService when the completed cart is not present"() {
        given:
        UUID listId = Uuids.timeBased()
        ListEntity listEntity = listDataProvider.createListEntity(listId, "list title", "shopping", "s", guestId, "d")

        when:
        def actual = deleteListService.deleteList(guestId,listId).block()

        then:
        1 * listRepository.findListById(listId) >> Mono.just(listEntity)
        1 * listRepository.deleteList(listEntity) >> Mono.just(listEntity)
        1 * eventPublisher.publishEvent(DeleteListNotifyEvent.getEventType(), _ , guestId) >>  Mono.just(GroovyMock(RecordMetadata))
        actual.listId == listId
    }


    def "Test deleteListService when list not found"() {
        given:
        UUID listId = UUID.randomUUID()

        when:
        def actual = deleteListService.deleteList(guestId,listId).block()

        then:
        1 * listRepository.findListById(listId) >> Mono.empty()
        actual.listId == listId
    }

    def "Test deleteListService with exception from findListById"() {
        given:
        UUID listId = UUID.randomUUID()

        when:
        deleteListService.deleteList(guestId,listId).block()

        then:
        1 * listRepository.findListById(listId) >> Mono.error(new RuntimeException("some exception"))
        thrown(RuntimeException)
    }

    def "Test deleteListService with exception from deleteList"() {
        given:
        UUID listId = Uuids.timeBased()
        ListEntity listEntity = listDataProvider.createListEntity(listId, "list title", "shopping", "s", guestId, "d")

        when:
        deleteListService.deleteList(guestId,listId).block()

        then:
        1 * listRepository.findListById(listId) >> Mono.just(listEntity)
        1 * listRepository.deleteList(listEntity) >> Mono.error(new RuntimeException("some exception"))
        thrown(RuntimeException)
    }
}
