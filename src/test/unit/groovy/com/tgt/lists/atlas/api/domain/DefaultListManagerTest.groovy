package com.tgt.lists.atlas.api.domain

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.util.LIST_MARKER
import com.tgt.lists.atlas.kafka.model.UpdateListNotifyEvent
import com.tgt.lists.atlas.util.ListDataProvider
import com.tgt.lists.common.components.exception.BadRequestException
import org.apache.kafka.clients.producer.RecordMetadata
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Instant

class DefaultListManagerTest extends Specification {

    ListRepository listRepository
    EventPublisher eventPublisher
    ListDataProvider listDataProvider
    UpdateListManager updateListManager
    DefaultListManager defaultListManager
    String guestId = "1234"
    String listType = "SHOPPING"

    def setup() {
        listRepository = Mock(ListRepository)
        eventPublisher = Mock(EventPublisher)
        listDataProvider = new ListDataProvider()
        updateListManager = new UpdateListManager(listRepository, eventPublisher)
        defaultListManager = new DefaultListManager(listRepository, updateListManager, 3, listType, false)
    }

    def "Test processDefaultListInd() while creating a list with default list as true and no preexisting lists present"() {
        when:
        def actual = defaultListManager.processDefaultListInd(guestId, true, null).block()

        then:
        1 * listRepository.findGuestLists(guestId, listType) >> Mono.just([])

        actual
    }

    def "Test processDefaultListInd() while creating a list with default list as false and no preexisting lists present"() {
        when:
        def actual = defaultListManager.processDefaultListInd(guestId, false, null).block()

        then:
        1 * listRepository.findGuestLists(guestId, listType) >> Mono.just([])

        actual  // since its the first list created we make it default even if the request come with default list indicator as false
    }

    def "Test processDefaultListInd() while creating a list with default list as false and one non default preexisting list"() {
        given:
        UUID listId = Uuids.timeBased()
        def listEntity = listDataProvider.createListEntity(listId, "title1", listType, "s", guestId, "", Instant.now(), Instant.now())

        when:
        def actual = defaultListManager.processDefaultListInd(guestId, false, null).block()

        then:
        1 * listRepository.findGuestLists(guestId, listType) >> Mono.just([listEntity])

        actual
    }

    def "Test processDefaultListInd() while creating a list with default list as true and one non default preexisting list"() {
        given:
        UUID listId = Uuids.timeBased()
        def listEntity = listDataProvider.createListEntity(listId, "title1", listType, "s", guestId, "", Instant.now(), Instant.now())

        when:
        def actual = defaultListManager.processDefaultListInd(guestId, true, null).block()

        then:
        1 * listRepository.findGuestLists(guestId, listType) >> Mono.just([listEntity])

        actual
    }

    def "Test processDefaultListInd() while creating a list with default list as false and one default preexisting list"() {
        given:
        UUID listId = Uuids.timeBased()
        def listEntity = listDataProvider.createListEntity(listId, "title1", listType, "s", guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())

        when:
        def actual = defaultListManager.processDefaultListInd(guestId, false, null).block()

        then:
        1 * listRepository.findGuestLists(guestId, listType) >> Mono.just([listEntity]) // We only check for max lists count and no DefaultListInd processing is done since defaultListIndicator is false in the request.

        actual
    }

    def "Test processDefaultListInd() while creating a list with default list as true and one default preexisting list"() {
        given:
        UUID listId = Uuids.timeBased()
        def listEntity = listDataProvider.createListEntity(listId, "title1", listType, "s", guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())
        def updatedListEntity = listDataProvider.createListEntity(listId, "title1", listType, "s", guestId, "", Instant.now(), Instant.now())

        when:
        def actual = defaultListManager.processDefaultListInd(guestId, true, null).block()

        then:
        1 * listRepository.findGuestLists(guestId, listType) >> Mono.just([listEntity])
        1 * listRepository.updateList(_, _ as ListEntity) >> { arguments ->
            final ListEntity list = arguments[1]
            assert list.marker == ""
            Mono.just(updatedListEntity)
        }
        1 * eventPublisher.publishEvent(UpdateListNotifyEvent.eventType, _, _) >> Mono.just(GroovyMock(RecordMetadata))

        actual
    }

    def "Test processDefaultListInd() while updating a list with default list as true and one default preexisting list"() {
        given:
        UUID listId1 = Uuids.timeBased()
        UUID listId2 = Uuids.timeBased()

        def listEntity1 = listDataProvider.createListEntity(listId1, "title1", listType, "s", guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())
        def listEntity2 = listDataProvider.createListEntity(listId2, "title1", listType, "s", guestId, "", Instant.now(), Instant.now())

        def updatedListEntity = listDataProvider.createListEntity(listId1, "title1", listType, "s", guestId, "", Instant.now(), Instant.now())

        when:
        def actual = defaultListManager.processDefaultListInd(guestId, true, listId2).block()

        then:
        1 * listRepository.findGuestLists(guestId, listType) >> Mono.just([listEntity1, listEntity2])
        1 * listRepository.updateList(_, _ as ListEntity) >> { arguments ->
            final ListEntity list = arguments[1]
            assert list.id == listId1
            assert list.marker == ""
            Mono.just(updatedListEntity)
        }
        1 * eventPublisher.publishEvent(UpdateListNotifyEvent.eventType, _, _) >> Mono.just(GroovyMock(RecordMetadata))

        actual
    }

    def "Test processDefaultListInd() while updating a list with default list as true and it being the only list present"() {
        given:
        UUID listId = Uuids.timeBased()
        def listEntity = listDataProvider.createListEntity(listId, "title1", listType, "s", guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())

        when:
        def actual = defaultListManager.processDefaultListInd(guestId, true, listId).block()

        then:
        1 * listRepository.findGuestLists(guestId, listType) >> Mono.just([listEntity])

        actual
    }

    def "Test processDefaultListInd() while updating a list with default list as true and one non default preexisting list"() {
        given:
        UUID listId1 = Uuids.timeBased()
        UUID listId2 = Uuids.timeBased()

        def listEntity1 = listDataProvider.createListEntity(listId1, "title1", listType, "s", guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())
        def listEntity2 = listDataProvider.createListEntity(listId2, "title1", listType, "s", guestId, "", Instant.now(), Instant.now())


        when:
        def actual = defaultListManager.processDefaultListInd(guestId, true, listId1).block()

        then:
        1 * listRepository.findGuestLists(guestId, listType) >> Mono.just([listEntity1, listEntity2])

        actual
    }

    def "Test processDefaultListInd() while a creating lists exceeding the max lists count"() {
        given:
        def listEntity1 = listDataProvider.createListEntity(Uuids.timeBased(), "title1", listType, "s", guestId, LIST_MARKER.DEFAULT.value, Instant.now(), Instant.now())
        def listEntity2 = listDataProvider.createListEntity(Uuids.timeBased(), "title2", listType, "s", guestId, "", Instant.now(), Instant.now())
        def listEntity3 = listDataProvider.createListEntity(Uuids.timeBased(), "title3", listType, "s", guestId, "", Instant.now(), Instant.now())

        when:
        defaultListManager.processDefaultListInd(guestId, false, null).block()

        then:
        1 * listRepository.findGuestLists(guestId, listType) >> Mono.just([listEntity1, listEntity2, listEntity3])

        thrown(BadRequestException)
    }
}
