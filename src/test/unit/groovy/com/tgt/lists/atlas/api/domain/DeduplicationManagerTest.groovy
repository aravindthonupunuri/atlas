package com.tgt.lists.atlas.api.domain

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.ListItemRequestTO
import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.api.util.UnitOfMeasure
import com.tgt.lists.atlas.util.CartDataProvider
import com.tgt.lists.atlas.util.ListDataProvider
import com.tgt.lists.atlas.util.TestListChannel
import com.tgt.lists.common.components.exception.BadRequestException
import org.apache.kafka.clients.producer.RecordMetadata
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

class DeduplicationManagerTest extends Specification {

    EventPublisher eventPublisher
    ListRepository listRepository
    DeduplicationManager deduplicationManager
    DeleteListItemsManager deleteListItemsManager
    InsertListItemsManager insertListItemsManager
    CartDataProvider cartDataProvider
    ListDataProvider listDataProvider
    String guestId = "1234"
    Long locationId = 1375L

    def setup() {
        listRepository = Mock(ListRepository)
        eventPublisher = Mock(EventPublisher)
        deleteListItemsManager = new DeleteListItemsManager(listRepository, eventPublisher)
        insertListItemsManager = new InsertListItemsManager(listRepository, eventPublisher)
        deduplicationManager = new DeduplicationManager(listRepository, insertListItemsManager, deleteListItemsManager, true, 5, 5, false)
        cartDataProvider = new CartDataProvider()
        listDataProvider = new ListDataProvider()
    }

    def "Test checkIfItemPreExist() for list with no preexisting items, so skipping dedup process"() {
        given:
        def listItemRequest = new ListItemRequestTO(ItemType.TCIN, "tcn1234",  TestListChannel.WEB.toString(),"1234", null,
            "itemNote", 1, UnitOfMeasure.EACHES, null)
        Map<String, ListItemRequestTO> newItemsMap = [ "tcn1234" : listItemRequest ] as LinkedHashMap
        def listId = UUID.randomUUID()

        when:
        def actual = deduplicationManager.updateDuplicateItems(guestId, listId, newItemsMap, LIST_ITEM_STATE.PENDING).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.empty()

        actual.first.isEmpty()
        actual.second.isEmpty()
    }

    def "Test checkIfItemPreExist() with no matching pending items"() {
        given:
        def listItemRequest = new ListItemRequestTO(ItemType.TCIN, "tcn1234",  TestListChannel.WEB.toString(), "1234", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        Map<String, ListItemRequestTO> newItemsMap = [ "tcn1234" : listItemRequest ] as LinkedHashMap
        def listId = Uuids.timeBased()

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, Uuids.timeBased(),
                LIST_ITEM_STATE.PENDING.name(), ItemType.TCIN.name(), "tcn5678", "5678", null,
                1, "notes1")

        when:
        def actual = deduplicationManager.updateDuplicateItems(guestId, listId, newItemsMap, LIST_ITEM_STATE.PENDING).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.just(listItemEntity)

        actual.first.isEmpty()
        actual.second.isEmpty()
    }

    def "Test checkIfItemPreExist() with no matching item type to dedup, so skipping dedup"() {
        given:
        def listItemRequest = new ListItemRequestTO(ItemType.TCIN, "tcn1234",  TestListChannel.WEB.toString(), "1234", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        Map<String, ListItemRequestTO> newItemsMap = [ "tcn1234" : listItemRequest ] as LinkedHashMap
        def listId = UUID.randomUUID()

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, Uuids.timeBased(),
                LIST_ITEM_STATE.PENDING.name(), ItemType.GENERIC_ITEM.name(), "tcn1234", null, null,
                1, "notes1")

        when:
        def actual = deduplicationManager.updateDuplicateItems(guestId, listId, newItemsMap, LIST_ITEM_STATE.PENDING).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.just(listItemEntity)

        actual.first.isEmpty()
        actual.second.isEmpty()
    }

    def "Test updateDuplicateItems() exceeding max pending items"() {
        given:
        def listItemRequest1 = new ListItemRequestTO(ItemType.TCIN, "tcn1234",  TestListChannel.WEB.toString(), "1234", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest2 = new ListItemRequestTO(ItemType.TCIN, "tcn2345",  TestListChannel.WEB.toString(), "2345", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest3 = new ListItemRequestTO(ItemType.TCIN, "tcn3456",  TestListChannel.WEB.toString(), "3456", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest4 = new ListItemRequestTO(ItemType.TCIN, "tcn4567",  TestListChannel.WEB.toString(), "4567", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest5 = new ListItemRequestTO(ItemType.TCIN, "tcn2222",  TestListChannel.WEB.toString(), "2222", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)

        Map<String, ListItemRequestTO> newItemsMap = [ "tcn1234" : listItemRequest1, "tcn2345" : listItemRequest2,
                                                       "tcn3456" : listItemRequest3, "tcn4567" : listItemRequest4,
                                                       "tcn2222" : listItemRequest5] as LinkedHashMap
        def listId = UUID.randomUUID()

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, Uuids.timeBased(),
                LIST_ITEM_STATE.PENDING.name(), ItemType.TCIN.name(), "tcn1111", "1111", null,
                1, "newItemNote")

        when:
        deduplicationManager.updateDuplicateItems(guestId, listId, newItemsMap, LIST_ITEM_STATE.PENDING).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.just(listItemEntity)

        thrown(BadRequestException)
    }

    def "Test updateDuplicateItems() exceeding max pending items but rolling update turned on"() {
        given:
        def listItemRequest1 = new ListItemRequestTO(ItemType.TCIN, "tcn1234",  TestListChannel.WEB.toString(), "1234", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest2 = new ListItemRequestTO(ItemType.TCIN, "tcn2345",  TestListChannel.WEB.toString(), "2345", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest3 = new ListItemRequestTO(ItemType.TCIN, "tcn3456",  TestListChannel.WEB.toString(), "3456", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest4 = new ListItemRequestTO(ItemType.TCIN, "tcn4567",  TestListChannel.WEB.toString(), "4567", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest5 = new ListItemRequestTO(ItemType.TCIN, "tcn2222",  TestListChannel.WEB.toString(), "2222", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)

        Map<String, ListItemRequestTO> newItemsMap = [ "tcn1234" : listItemRequest1, "tcn2345" : listItemRequest2,
                                                       "tcn3456" : listItemRequest3, "tcn4567" : listItemRequest4,
                                                       "tcn2222" : listItemRequest5] as LinkedHashMap
        def listId = UUID.randomUUID()

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, Uuids.timeBased(),
                LIST_ITEM_STATE.PENDING.name(), ItemType.TCIN.name(), "tcn1111", "1111", "new item",
                1, "newItemNote")

       def recordMetadata = GroovyMock(RecordMetadata)

        deduplicationManager = new DeduplicationManager(listRepository, insertListItemsManager, deleteListItemsManager,
                true, 5, 5, true) // turning on rolling update
        when:
        def actual = deduplicationManager.updateDuplicateItems(guestId, listId, newItemsMap, LIST_ITEM_STATE.PENDING).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.just(listItemEntity)
        1 * listRepository.deleteListItems(_) >> Mono.just([listItemEntity])
        1 * eventPublisher.publishEvent(_,_,_) >> Mono.just(recordMetadata)

        actual.first.isEmpty()
        actual.second.isEmpty()
    }

    def "Test updateDuplicateItems() exceeding max pending items but rolling update turned on and dedupe turned off"() {
        given:
        def listItemRequest1 = new ListItemRequestTO(ItemType.TCIN, "tcn1234",  TestListChannel.WEB.toString(), "1234", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest2 = new ListItemRequestTO(ItemType.TCIN, "tcn2345",  TestListChannel.WEB.toString(), "2345", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest3 = new ListItemRequestTO(ItemType.TCIN, "tcn3456",  TestListChannel.WEB.toString(), "3456", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest4 = new ListItemRequestTO(ItemType.TCIN, "tcn4567",  TestListChannel.WEB.toString(), "4567", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest5 = new ListItemRequestTO(ItemType.TCIN, "tcn2222",  TestListChannel.WEB.toString(), "2222", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)

        Map<String, ListItemRequestTO> newItemsMap = [ "tcn1234" : listItemRequest1, "tcn2345" : listItemRequest2,
                                                       "tcn3456" : listItemRequest3, "tcn4567" : listItemRequest4,
                                                       "tcn2222" : listItemRequest5] as LinkedHashMap
        def listId = UUID.randomUUID()

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, Uuids.timeBased(),
                LIST_ITEM_STATE.PENDING.name(), ItemType.TCIN.name(), "tcn1234", "1234", "new item",
                1, "newItemNote")

        def recordMetadata = GroovyMock(RecordMetadata)

        deduplicationManager = new DeduplicationManager(listRepository, insertListItemsManager, deleteListItemsManager,
                false, 5, 5, true) // turning on rolling update and dedupe turned off
        when:
        def actual = deduplicationManager.updateDuplicateItems(guestId, listId, newItemsMap, LIST_ITEM_STATE.PENDING).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.just(listItemEntity)
        1 * listRepository.deleteListItems(_) >> Mono.just([listItemEntity])
        1 * eventPublisher.publishEvent(_,_,_) >> Mono.just(recordMetadata)

        actual.first.isEmpty()
        actual.second.isEmpty()

    }

    def "Test updateDuplicateItems() exceeding max pending items with rolling update turned on and not exceeding max count"() {
        given:
        def listItemRequest1 = new ListItemRequestTO(ItemType.TCIN, "tcn1234",  TestListChannel.WEB.toString(), "1234", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest2 = new ListItemRequestTO(ItemType.TCIN, "tcn2345",  TestListChannel.WEB.toString(), "2345", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest3 = new ListItemRequestTO(ItemType.TCIN, "tcn3456",  TestListChannel.WEB.toString(), "3456", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest4 = new ListItemRequestTO(ItemType.TCIN, "tcn4567",  TestListChannel.WEB.toString(), "4567", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest5 = new ListItemRequestTO(ItemType.TCIN, "tcn2222",  TestListChannel.WEB.toString(), "2222", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)

        Map<String, ListItemRequestTO> newItemsMap = [ "tcn1234" : listItemRequest1, "tcn2345" : listItemRequest2,
                                                       "tcn3456" : listItemRequest3, "tcn4567" : listItemRequest4,
                                                       "tcn2222" : listItemRequest5] as LinkedHashMap
        def listId = UUID.randomUUID()

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, Uuids.timeBased(),
                LIST_ITEM_STATE.PENDING.name(), ItemType.TCIN.name(), "tcn1111", "1111", "new item",
                1, "newItemNote")

        deduplicationManager = new DeduplicationManager(listRepository, insertListItemsManager, deleteListItemsManager,
                true, 10, 5, true) // turning on rolling update
        when:
        def actual = deduplicationManager.updateDuplicateItems(guestId, listId, newItemsMap, LIST_ITEM_STATE.PENDING).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.just(listItemEntity)

        actual.first.isEmpty()
        actual.second.isEmpty()
    }

    def "Test updateDuplicateItems() with rolling update turned on and dedup"() {
        given:
        def listItemRequest1 = new ListItemRequestTO(ItemType.TCIN, "tcn1234",  TestListChannel.WEB.toString(), "1234", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest2 = new ListItemRequestTO(ItemType.TCIN, "tcn2345",  TestListChannel.WEB.toString(), "2345", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest3 = new ListItemRequestTO(ItemType.TCIN, "tcn3456",  TestListChannel.WEB.toString(), "3456", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest4 = new ListItemRequestTO(ItemType.TCIN, "tcn4567",  TestListChannel.WEB.toString(), "4567", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest5 = new ListItemRequestTO(ItemType.TCIN, "tcn1111",  TestListChannel.WEB.toString(), "1111", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)

        Map<String, ListItemRequestTO> newItemsMap = [ "tcn1234" : listItemRequest1, "tcn2345" : listItemRequest2,
                                                       "tcn3456" : listItemRequest3, "tcn4567" : listItemRequest4,
                                                       "tcn1111" : listItemRequest5] as LinkedHashMap
        def listId = UUID.randomUUID()
        def recordMetadata = GroovyMock(RecordMetadata)

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, Uuids.timeBased(),
                LIST_ITEM_STATE.PENDING.name(), ItemType.TCIN.name(), "tcn1234", "1234", "new item",
                1, "newItemNote")

        ListItemEntity updatedListItemEntity = listDataProvider.createListItemEntity(listId, listItemEntity.itemId,
                LIST_ITEM_STATE.PENDING.name(), ItemType.TCIN.name(), listItemEntity.itemRefId, listItemEntity.itemTcin,
                "new item", listItemRequest1.requestedQuantity + listItemEntity.itemReqQty,
                "newItemNote\nitemNote")

        deduplicationManager = new DeduplicationManager(listRepository, insertListItemsManager, deleteListItemsManager,
                true, 5, 5, true) // turning on rolling update
        when:
        def actual = deduplicationManager.updateDuplicateItems(guestId, listId, newItemsMap, LIST_ITEM_STATE.PENDING).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.just(listItemEntity)
        // updating duplicate items
        1 * listRepository.saveListItems(_ as List<ListItemEntity>) >> { arguments ->
            final List<ListItemEntity> listItems = arguments[0]
            assert listItems.size() == 1
            assert listItems.first().id == listId
            assert listItems.first().itemReqQty == listItemRequest1.requestedQuantity + listItemEntity.itemReqQty
            Mono.just([updatedListItemEntity])
        }
        1 * eventPublisher.publishEvent(_,_,_) >> Mono.just(recordMetadata)

        actual.first.size() == 1
        actual.second.size() == 1

    }

    def "Test updateDuplicateItems() with multiple PreExisting items"() {
        given:
        def listItemRequest1 = new ListItemRequestTO(ItemType.TCIN, "tcn1234",  TestListChannel.WEB.toString(), "1234", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest2 = new ListItemRequestTO(ItemType.TCIN, "tcn2345",  TestListChannel.WEB.toString(), "2345", null,
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest3 = new ListItemRequestTO(ItemType.GENERIC_ITEM, "itm3456",  TestListChannel.WEB.toString(), null, "genericItem1",
                "itemNote", 1, UnitOfMeasure.EACHES, null)
        def listItemRequest4 = new ListItemRequestTO(ItemType.GENERIC_ITEM, "itm4567", TestListChannel.WEB.toString(), null,  "genericItem2",
                "itemNote", 1, UnitOfMeasure.EACHES, null)

        Map<String, ListItemRequestTO> newItemsMap = [ "tcn1234" : listItemRequest1, "tcn2345" : listItemRequest2,
                                                       "itm3456" : listItemRequest3, "itm4567" : listItemRequest4] as LinkedHashMap
        def listId = UUID.randomUUID()

        ListItemEntity listItemEntity1 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(),
                LIST_ITEM_STATE.PENDING.name(), ItemType.TCIN.name(), "tcn1234", "1234", "new item",
                1, "note1")

        ListItemEntity listItemEntity2 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(),
                LIST_ITEM_STATE.PENDING.name(), ItemType.GENERIC_ITEM.name(), "itm3456", null, "genericItem1",
                1, "note2")

        ListItemEntity listItemEntity3 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(),
                LIST_ITEM_STATE.PENDING.name(), ItemType.GENERIC_ITEM.name(), "itm3456", null, "genericItem1",
                1, "note3")

        ListItemEntity updatedListItemEntity1 = listDataProvider.createListItemEntity(listId, listItemEntity1.itemId,
                listItemEntity1.itemState, listItemEntity1.itemType, listItemEntity1.itemRefId, listItemEntity1.itemTcin,
                listItemEntity1.itemTitle, listItemRequest1.requestedQuantity + listItemEntity1.itemReqQty,
                "note1\nitemNote")

        ListItemEntity updatedListItemEntity2 = listDataProvider.createListItemEntity(listId, listItemEntity2.itemId,
                listItemEntity2.itemState, listItemEntity2.itemType, listItemEntity2.itemRefId, listItemEntity2.itemTcin,
                listItemEntity2.itemTitle, listItemRequest3.requestedQuantity + listItemEntity2.itemReqQty + listItemEntity3.itemReqQty,
                "note2\nnote3\nitemNote")

        def recordMetadata = GroovyMock(RecordMetadata)

        when:
        def actual = deduplicationManager.updateDuplicateItems(guestId, listId, newItemsMap, LIST_ITEM_STATE.PENDING).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.just(listItemEntity1, listItemEntity2, listItemEntity3)
        // updating duplicate items
        1 * listRepository.saveListItems(_ as List<ListItemEntity>) >> { arguments ->
            final List<ListItemEntity> listItems = arguments[0]
            assert listItems.size() == 2
            assert listItems[0].itemId == listItemEntity1.itemId
            assert listItems[0].itemReqQty == listItemRequest1.requestedQuantity + listItemEntity1.itemReqQty
            assert listItems[1].itemId == listItemEntity2.itemId
            assert listItems[1].itemReqQty == listItemRequest3.requestedQuantity + listItemEntity2.itemReqQty + listItemEntity3.itemReqQty


            Mono.just([updatedListItemEntity1, updatedListItemEntity2])
        }
        // deleting duplicate items
        1 * listRepository.deleteListItems(_ as List<ListItemEntity>) >> { arguments ->
            final List<ListItemEntity> listItems = arguments[0]
            assert listItems.size() == 1
            assert listItems[0].id == listId
            assert listItems[0].itemId == listItemEntity3.itemId
            Mono.just([listItemEntity3])
        }
        3 * eventPublisher.publishEvent(_,_,_) >> Mono.just(recordMetadata)

        actual.first.size() == 2
        actual.second.size() == 2
    }
}
