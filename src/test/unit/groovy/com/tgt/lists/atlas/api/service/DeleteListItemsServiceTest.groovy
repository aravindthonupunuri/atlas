package com.tgt.lists.atlas.api.service

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.api.domain.DeleteListItemsManager
import com.tgt.lists.atlas.api.domain.EventPublisher
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.util.ItemIncludeFields
import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.kafka.model.DeleteListItemNotifyEvent
import com.tgt.lists.atlas.util.CartDataProvider
import com.tgt.lists.atlas.util.ListDataProvider
import com.tgt.lists.common.components.exception.BadRequestException
import org.apache.kafka.clients.producer.RecordMetadata
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

class DeleteListItemsServiceTest extends Specification {
    
    ListRepository listRepository
    EventPublisher eventPublisher
    DeleteListItemsManager deleteListItemsManager
    DeleteListItemsService deleteListItemsService
    CartDataProvider cartDataProvider
    ListDataProvider listDataProvider
    String guestId = "1234"

    def setup() {
        listRepository = Mock(ListRepository)
        eventPublisher = Mock(EventPublisher)
        deleteListItemsManager = new DeleteListItemsManager(listRepository, eventPublisher)
        deleteListItemsService = new DeleteListItemsService(deleteListItemsManager, listRepository)
        cartDataProvider = new CartDataProvider()
        listDataProvider = new ListDataProvider()
    }

    def "Test delete cart items with ItemIncludeFields ALL"() {
        given:
        def listId = Uuids.timeBased()
        def recordMetadata = GroovyMock(RecordMetadata)

        ListItemEntity listItemEntity1 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "item1234", "1234", null, 1, "notes1")
        ListItemEntity listItemEntity2 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "item1235", "1235", null, 1, "notes2")
        ListItemEntity listItemEntity3 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "item1236", "1236", null, 1, "notes3")

        when:
        def actual = deleteListItemsService.deleteListItems(guestId, listId, null, ItemIncludeFields.ALL).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.just(listItemEntity1, listItemEntity2, listItemEntity3)
        1 * listRepository.deleteListItems(_ as List<ListItemEntity>) >> { arguments ->
            final List<ListItemEntity> listItems = arguments[0]
            assert listItems.size() == 3
            assert listItems[0].id == listId
            Mono.just([listItemEntity1, listItemEntity2, listItemEntity3])
        }
        3 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata)

        actual.listId == listId
        actual.itemIds.size() == 3
    }

    def "Test delete cart items with ItemIncludeFields PENDING"() {
        given:
        def listId = Uuids.timeBased()
        def recordMetadata = GroovyMock(RecordMetadata)

        ListItemEntity listItemEntity1 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "item1234", "1234", null, 1, "notes1")
        ListItemEntity listItemEntity2 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "item1235", "1235", null, 1, "notes2")
        ListItemEntity listItemEntity3 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "item1236", "1236", null, 1, "notes3")

        when:
        def actual = deleteListItemsService.deleteListItems(guestId, listId, null, ItemIncludeFields.PENDING).block()

        then:
        1 * listRepository.findListItemsByListIdAndItemState(listId, LIST_ITEM_STATE.PENDING.value) >> Flux.just(listItemEntity1, listItemEntity2, listItemEntity3)
        1 * listRepository.deleteListItems(_ as List<ListItemEntity>) >> { arguments ->
            final List<ListItemEntity> listItems = arguments[0]
            assert listItems.size() == 3
            assert listItems[0].id == listId
            Mono.just([listItemEntity1, listItemEntity2, listItemEntity3])
        }
        3 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata)

        actual.listId == listId
        actual.itemIds.size() == 3
    }

    def "Test delete items with ItemIncludeFields COMPLETED"() {
        given:
        def listId = Uuids.timeBased()
        def recordMetadata = GroovyMock(RecordMetadata)

        ListItemEntity listItemEntity1 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.COMPLETED.value, ItemType.TCIN.value, "item1234", "1234", null, 1, "notes1")
        ListItemEntity listItemEntity2 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.COMPLETED.value, ItemType.TCIN.value, "item1235", "1235", null, 1, "notes2")
        ListItemEntity listItemEntity3 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.COMPLETED.value, ItemType.TCIN.value, "item1236", "1236", null, 1, "notes3")

        when:
        def actual = deleteListItemsService.deleteListItems(guestId, listId, null, ItemIncludeFields.COMPLETED).block()

        then:
        1 * listRepository.findListItemsByListIdAndItemState(listId, LIST_ITEM_STATE.COMPLETED.value) >> Flux.just(listItemEntity1, listItemEntity2, listItemEntity3)
        1 * listRepository.deleteListItems(_ as List<ListItemEntity>) >> { arguments ->
            final List<ListItemEntity> listItems = arguments[0]
            assert listItems.size() == 3
            assert listItems[0].id == listId
            Mono.just([listItemEntity1, listItemEntity2, listItemEntity3])
        }
        3 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata)

        actual.listId == listId
        actual.itemIds.size() == 3
    }



    def "Test delete items with ItemIncludeFields PENDING with no pending items to delete"() {
        given:
        def listId = Uuids.timeBased()

        when:
        def actual = deleteListItemsService.deleteListItems(guestId, listId, null, ItemIncludeFields.PENDING).block()

        then:
        1 * listRepository.findListItemsByListIdAndItemState(listId, LIST_ITEM_STATE.PENDING.value) >> Flux.empty()

        actual.listId == listId
        actual.itemIds.size() == 0
    }

    def "Test delete items with ItemIncludeFields ALL with exception getting list items"() {
        given:
        def listId = Uuids.timeBased()

        when:
        deleteListItemsService.deleteListItems(guestId, listId, null, ItemIncludeFields.ALL).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.error(new RuntimeException("some exception"))

        thrown(RuntimeException)
    }

    def "Test delete items with ItemIncludeFields PENDING with exception getting pending list items"() {
        given:
        def listId = Uuids.timeBased()

        when:
        deleteListItemsService.deleteListItems(guestId, listId, null, ItemIncludeFields.PENDING).block()

        then:
        1 * listRepository.findListItemsByListIdAndItemState(listId, LIST_ITEM_STATE.PENDING.value) >> Flux.error(new RuntimeException("some exception"))

        thrown(RuntimeException)
    }

    def "Test delete items with ItemIncludeFields ALL with exception deleting list items"() {
        given:
        def listId = Uuids.timeBased()

        ListItemEntity listItemEntity1 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "item1234", "1234", null, 1, "notes1")
        ListItemEntity listItemEntity2 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "item1235", "1235", null, 1, "notes2")
        ListItemEntity listItemEntity3 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "item1236", "1236", null, 1, "notes3")

        when:
        deleteListItemsService.deleteListItems(guestId, listId, null, ItemIncludeFields.PENDING).block()

        then:
        1 * listRepository.findListItemsByListIdAndItemState(listId, LIST_ITEM_STATE.PENDING.value) >> Flux.just(listItemEntity1, listItemEntity2, listItemEntity3)
        1 * listRepository.deleteListItems(_ as List<ListItemEntity>) >> Mono.error(new RuntimeException("some error"))

        thrown(RuntimeException)
    }

    def "Test delete items with ItemIncludeFields PENDING with exception deleting pending list items"() {
        given:
        def listId = Uuids.timeBased()

        ListItemEntity listItemEntity1 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "item1234", "1234", null, 1, "notes1")
        ListItemEntity listItemEntity2 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "item1235", "1235", null, 1, "notes2")
        ListItemEntity listItemEntity3 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "item1236", "1236", null, 1, "notes3")

        when:
        deleteListItemsService.deleteListItems(guestId, listId, null, ItemIncludeFields.PENDING).block()

        then:
        1 * listRepository.findListItemsByListIdAndItemState(listId, LIST_ITEM_STATE.PENDING.value) >> Flux.just(listItemEntity1, listItemEntity2, listItemEntity3)
        1 * listRepository.deleteListItems(_ as List<ListItemEntity>) >> Mono.error(new RuntimeException("some error"))

        thrown(RuntimeException)
    }

    def "Test delete items with when listItemIds to be deleted are passed as part of request"() {
        given:
        def listId = Uuids.timeBased()
        def listItemId1 = Uuids.timeBased()
        def listItemId2 = Uuids.timeBased()
        def listItemId3 = Uuids.timeBased()

        def recordMetadata = GroovyMock(RecordMetadata)

        ListItemEntity listItemEntity1 = listDataProvider.createListItemEntity(listId, listItemId1, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "item1234", "1234", null, 1, "notes1")
        ListItemEntity listItemEntity2 = listDataProvider.createListItemEntity(listId, listItemId2, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "item1235", "1235", null, 1, "notes2")
        ListItemEntity listItemEntity3 = listDataProvider.createListItemEntity(listId, listItemId3, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "item1236", "1236", null, 1, "notes3")

        when:
        def actual = deleteListItemsService.deleteListItems(guestId, listId, [listItemId1, listItemId2], null).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.just(listItemEntity1, listItemEntity2, listItemEntity3)
        1 * listRepository.deleteListItems(_ as List<ListItemEntity>) >> { arguments ->
            final List<ListItemEntity> listItems = arguments[0]
            assert listItems.size() == 2
            Mono.just([listItemEntity1, listItemEntity2])
        }
        2 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _ , listId.toString()) >>  Mono.just(recordMetadata)

        actual.listId == listId
        actual.itemIds.size() == 2
        actual.itemIds.contains(listItemId1)
        actual.itemIds.contains(listItemId2)
    }

    def "Test delete items with when listItemIds to be deleted are passed with exception getting items from list"() {
        given:
        def listId = Uuids.timeBased()
        def listItemId1 = Uuids.timeBased()
        def listItemId2 = Uuids.timeBased()

        when:
        deleteListItemsService.deleteListItems(guestId, listId, [listItemId1, listItemId2], null).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.error(new RuntimeException("some error"))

        thrown(RuntimeException)

    }

    def "Test delete items with ItemIncludeFields ALL but items to delete in the list"() {
        given:
        def listId = Uuids.timeBased()

        when:
        def actual = deleteListItemsService.deleteListItems(guestId, listId, null, ItemIncludeFields.ALL).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.empty()

        actual.listId == listId
        actual.itemIds.size() == 0
    }

    def "Test delete items with ItemIncludeFields PENDING but no pending items to delete in the list"() {
        given:
        def listId = Uuids.timeBased()

        when:
        def actual = deleteListItemsService.deleteListItems(guestId, listId, null, ItemIncludeFields.PENDING).block()

        then:
        1 * listRepository.findListItemsByListIdAndItemState(listId, LIST_ITEM_STATE.PENDING.value) >> Flux.empty()

        actual.listId == listId
        actual.itemIds.size() == 0
    }

    def "Test delete items with when listItemIds to be deleted are passed but not items to delete in list"() {
        given:
        def listId = Uuids.timeBased()
        def listItemId1 = Uuids.timeBased()
        def listItemId2 = Uuids.timeBased()

        when:
        def actual = deleteListItemsService.deleteListItems(guestId, listId, [listItemId1, listItemId2], null).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.empty()

        actual.listId == listId
        actual.itemIds.size() == 0
    }

    def "Test delete items  when both itemIds and itemIncludeFields given"() {
        given:
        def listId = Uuids.timeBased()
        def listItemId1 = Uuids.timeBased()
        def listItemId2 = Uuids.timeBased()

        when:
        deleteListItemsService.deleteListItems(guestId, listId, [listItemId1, listItemId2], ItemIncludeFields.ALL).block()

        then:
        thrown(BadRequestException)
    }
}
