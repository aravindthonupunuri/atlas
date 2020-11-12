package com.tgt.lists.atlas.api.domain

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListPreferenceEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListPreferenceRepository
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.service.ListItemSortOrderService
import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.util.ListDataProvider
import com.tgt.lists.common.components.exception.BadRequestException
import org.apache.kafka.clients.producer.RecordMetadata
import reactor.core.publisher.Mono
import spock.lang.Specification

class DeduplicationManagerTest extends Specification {

    EventPublisher eventPublisher
    ListRepository listRepository
    ListPreferenceRepository listPreferenceRepository
    ListItemSortOrderService listItemSortOrderService
    DeduplicationManager deduplicationManager
    ListPreferenceSortOrderManager listPreferenceSortOrderManager
    DeleteListItemsManager deleteListItemsManager
    UpdateListItemManager updateListItemManager
    ListDataProvider listDataProvider
    String guestId = "1234"

    def setup() {
        listRepository = Mock(ListRepository)
        eventPublisher = Mock(EventPublisher)
        listPreferenceRepository = Mock(ListPreferenceRepository)
        listPreferenceSortOrderManager = new ListPreferenceSortOrderManager(listPreferenceRepository)
        listItemSortOrderService = new ListItemSortOrderService(listPreferenceSortOrderManager)
        deleteListItemsManager = new DeleteListItemsManager(listRepository, eventPublisher, listItemSortOrderService)
        updateListItemManager = new UpdateListItemManager(listRepository, eventPublisher)
        deduplicationManager = new DeduplicationManager(listRepository, updateListItemManager, deleteListItemsManager, true, 5, 5, false)
        listDataProvider = new ListDataProvider()
    }

    def "Test checkIfItemPreExist() for list with no preexisting items, so skipping dedup process"() {
        given:
        def listId = Uuids.timeBased()
        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn1234", "1234", null, 1, "itemNote")

        when:
        def actual = deduplicationManager.updateDuplicateItems(guestId, listId, [listItemEntity], [], LIST_ITEM_STATE.PENDING).block()

        then:
        actual.isEmpty()
    }

    def "Test updateDuplicateItems() with no matching pending items"() {
        given:
        def listId = Uuids.timeBased()
        ListItemEntity listItemEntity1 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn1234", "1234", null, 1, "itemNote")
        ListItemEntity listItemEntity2 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, listDataProvider.getItemRefId(ItemType.TCIN, "5678"), "5678", null, 1, "notes1")

        when:
        def actual = deduplicationManager.updateDuplicateItems(guestId, listId, [listItemEntity1], [listItemEntity2], LIST_ITEM_STATE.PENDING).block()

        then:
        actual.isEmpty()
    }

    def "Test updateDuplicateItems() with no matching item type to dedup, so skipping dedup"() {
        given:
        def listId = Uuids.timeBased()
        ListItemEntity listItemEntity1 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn1234", "1234", null, 1, "itemNote")
        ListItemEntity listItemEntity2 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.GENERIC_ITEM.value, "gnrtitle", null, "title", 1, "notes1")

        when:
        def actual = deduplicationManager.updateDuplicateItems(guestId, listId, [listItemEntity1], [listItemEntity2], LIST_ITEM_STATE.PENDING).block()

        then:
        actual.isEmpty()
    }

    def "Test updateDuplicateItems() exceeding max pending items"() {
        given:
        def listId = Uuids.timeBased()
        ListItemEntity listItemEntity1 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn1234", "1234", null, 1, "itemNote")
        ListItemEntity listItemEntity2 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn2345", "2345", null, 1, "itemNote")
        ListItemEntity listItemEntity3 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn3456", "3456", null, 1, "itemNote")
        ListItemEntity listItemEntity4 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn4567", "4567", null, 1, "itemNote")
        ListItemEntity listItemEntity5 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn2222", "2222", null, 1, "itemNote")

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn1111", "1111", null, 1, "newItemNote")

        when:
        deduplicationManager.updateDuplicateItems(guestId, listId, [listItemEntity1 ,listItemEntity2, listItemEntity3, listItemEntity4, listItemEntity5], [listItemEntity], LIST_ITEM_STATE.PENDING).block()

        then:
        thrown(BadRequestException)
    }

    def "Test updateDuplicateItems() exceeding max pending items but rolling update turned on"() {
        given:
        def listId = Uuids.timeBased()
        ListItemEntity listItemEntity1 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn1234", "1234", null, 1, "itemNote")
        ListItemEntity listItemEntity2 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn2345", "2345", null, 1, "itemNote")
        ListItemEntity listItemEntity3 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn3456", "3456", null, 1, "itemNote")
        ListItemEntity listItemEntity4 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn4567", "4567", null, 1, "itemNote")
        ListItemEntity listItemEntity5 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn2222", "2222", null, 1, "itemNote")

        def listItemId = Uuids.timeBased()

        ListPreferenceEntity preUpdateListPreferenceEntity = listDataProvider.createListPreferenceEntity(listId, guestId, listItemId.toString())
        ListPreferenceEntity posUpdateListPreferenceEntity = listDataProvider.getListPreferenceEntity(listId, guestId)

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, listItemId,
                LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn1111", "1111", "new item",
                1, "newItemNote")

       def recordMetadata = GroovyMock(RecordMetadata)

        deduplicationManager = new DeduplicationManager(listRepository, updateListItemManager, deleteListItemsManager,
                true, 5, 5, true) // turning on rolling update
        when:
        def actual = deduplicationManager.updateDuplicateItems(guestId, listId, [listItemEntity1 ,listItemEntity2, listItemEntity3, listItemEntity4, listItemEntity5], [listItemEntity], LIST_ITEM_STATE.PENDING).block()

        then:
        1 * listPreferenceRepository.getListPreference(_,_) >> Mono.just(preUpdateListPreferenceEntity)
        1 * listPreferenceRepository.saveListPreference(_) >> Mono.just(posUpdateListPreferenceEntity)
        1 * listRepository.deleteListItems(_) >> Mono.just([listItemEntity])
        1 * eventPublisher.publishEvent(_,_,_) >> Mono.just(recordMetadata)

        actual.isEmpty()
    }

    def "Test updateDuplicateItems() exceeding max pending items, rolling update turned on and existing item isnt part of item sort order"() {
        given:
        def listId = Uuids.timeBased()
        ListItemEntity listItemEntity1 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn1234", "1234", null, 1, "itemNote")
        ListItemEntity listItemEntity2 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn2345", "2345", null, 1, "itemNote")
        ListItemEntity listItemEntity3 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn3456", "3456", null, 1, "itemNote")
        ListItemEntity listItemEntity4 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn4567", "4567", null, 1, "itemNote")
        ListItemEntity listItemEntity5 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn2222", "2222", null, 1, "itemNote")

        def listItemId = Uuids.timeBased()

        ListPreferenceEntity preUpdateListPreferenceEntity = listDataProvider.createListPreferenceEntity(listId, guestId, Uuids.timeBased().toString())

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, listItemId,
                LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn1111", "1111", "new item",
                1, "newItemNote")

        def recordMetadata = GroovyMock(RecordMetadata)

        deduplicationManager = new DeduplicationManager(listRepository, updateListItemManager, deleteListItemsManager,
                true, 5, 5, true) // turning on rolling update
        when:
        def actual = deduplicationManager.updateDuplicateItems(guestId, listId, [listItemEntity1 ,listItemEntity2, listItemEntity3, listItemEntity4, listItemEntity5], [listItemEntity], LIST_ITEM_STATE.PENDING).block()

        then:
        1 * listPreferenceRepository.getListPreference(_,_) >> Mono.just(preUpdateListPreferenceEntity)
        1 * listRepository.deleteListItems(_) >> Mono.just([listItemEntity])
        1 * eventPublisher.publishEvent(_,_,_) >> Mono.just(recordMetadata)

        actual.isEmpty()
    }

    def "Test updateDuplicateItems() exceeding max pending items but rolling update turned on and dedupe turned off"() {
        given:
        def listId = Uuids.timeBased()
        ListItemEntity listItemEntity1 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn1234", "1234", null, 1, "itemNote")
        ListItemEntity listItemEntity2 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn2345", "2345", null, 1, "itemNote")
        ListItemEntity listItemEntity3 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn3456", "3456", null, 1, "itemNote")
        ListItemEntity listItemEntity4 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn4567", "4567", null, 1, "itemNote")
        ListItemEntity listItemEntity5 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn2222", "2222", null, 1, "itemNote")

        def listItemId = Uuids.timeBased()

        ListPreferenceEntity preUpdateListPreferenceEntity = listDataProvider.createListPreferenceEntity(listId, guestId, listItemId.toString())
        ListPreferenceEntity posUpdateListPreferenceEntity = listDataProvider.getListPreferenceEntity(listId, guestId)

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, listItemId, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn1234", "1234", "new item", 1, "newItemNote")

        def recordMetadata = GroovyMock(RecordMetadata)

        deduplicationManager = new DeduplicationManager(listRepository, updateListItemManager, deleteListItemsManager,
                false, 5, 5, true) // turning on rolling update and dedupe turned off
        when:
        def actual = deduplicationManager.updateDuplicateItems(guestId, listId, [listItemEntity1 ,listItemEntity2, listItemEntity3, listItemEntity4, listItemEntity5], [listItemEntity], LIST_ITEM_STATE.PENDING).block()

        then:
        1 * listPreferenceRepository.getListPreference(_,_) >> Mono.just(preUpdateListPreferenceEntity)
        1 * listPreferenceRepository.saveListPreference(_) >> Mono.just(posUpdateListPreferenceEntity)
        1 * listRepository.deleteListItems(_) >> Mono.just([listItemEntity])
        1 * eventPublisher.publishEvent(_,_,_) >> Mono.just(recordMetadata)

        actual.isEmpty()
    }

    def "Test updateDuplicateItems() exceeding max pending items with rolling update turned on and not exceeding max count"() {
        given:
        def listId = Uuids.timeBased()
        ListItemEntity listItemEntity1 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn1234", "1234", null, 1, "itemNote")
        ListItemEntity listItemEntity2 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn2345", "2345", null, 1, "itemNote")
        ListItemEntity listItemEntity3 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn3456", "3456", null, 1, "itemNote")
        ListItemEntity listItemEntity4 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn4567", "4567", null, 1, "itemNote")
        ListItemEntity listItemEntity5 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn2222", "2222", null, 1, "itemNote")

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn1111", "1111", "new item", 1, "newItemNote")

        deduplicationManager = new DeduplicationManager(listRepository, updateListItemManager, deleteListItemsManager,
                true, 10, 5, true) // turning on rolling update
        when:
        def actual = deduplicationManager.updateDuplicateItems(guestId, listId, [listItemEntity1 ,listItemEntity2, listItemEntity3, listItemEntity4, listItemEntity5], [listItemEntity], LIST_ITEM_STATE.PENDING).block()

        then:
        actual.isEmpty()
    }

    def "Test updateDuplicateItems() with rolling update turned on and dedup"() {
        given:
        def listId = Uuids.timeBased()
        ListItemEntity listItemEntity1 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn1234", "1234", null, 1, "itemNote")
        ListItemEntity listItemEntity2 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn2345", "2345", null, 1, "itemNote")
        ListItemEntity listItemEntity3 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn3456", "3456", null, 1, "itemNote")
        ListItemEntity listItemEntity4 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn4567", "4567", null, 1, "itemNote")
        ListItemEntity listItemEntity5 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn1111", "1111", null, 1, "itemNote")

        def recordMetadata = GroovyMock(RecordMetadata)

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn1234", "1234", "new item", 1, "newItemNote")

        ListItemEntity updatedListItemEntity = listDataProvider.createListItemEntity(listId, listItemEntity.itemId, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, listItemEntity.itemRefId, listItemEntity.itemTcin, "new item", listItemEntity1.itemReqQty + listItemEntity.itemReqQty, "newItemNote\nitemNote")

        deduplicationManager = new DeduplicationManager(listRepository, updateListItemManager, deleteListItemsManager,
                true, 5, 5, true) // turning on rolling update
        when:
        def actual = deduplicationManager.updateDuplicateItems(guestId, listId, [listItemEntity1 ,listItemEntity2, listItemEntity3, listItemEntity4, listItemEntity5], [listItemEntity], LIST_ITEM_STATE.PENDING).block()

        then:
        // updating duplicate items
        1 * listRepository.updateListItem(_ as ListItemEntity, null) >> { arguments ->
            final ListItemEntity listItem = arguments[0]
            assert listItem.id == listId
            assert listItem.itemReqQty == listItemEntity1.itemReqQty + listItemEntity.itemReqQty
            Mono.just(updatedListItemEntity)
        }
        1 * eventPublisher.publishEvent(_,_,_) >> Mono.just(recordMetadata)

        actual.size() == 1
    }

    def "Test updateDuplicateItems() with multiple PreExisting items"() {
        given:
        def listId = Uuids.timeBased()

        ListItemEntity listItemEntity1 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn1234", "1234", null, 1, "itemNote")
        ListItemEntity listItemEntity2 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn2345", "2345", null, 1, "itemNote")
        ListItemEntity listItemEntity3 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.GENERIC_ITEM.value, "itm3456", null, "genericItem1", 1, "itemNote")
        ListItemEntity listItemEntity4 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.GENERIC_ITEM.value, "itm4567", null, "genericItem2", 1, "itemNote")

        ListPreferenceEntity preUpdateListPreferenceEntity = listDataProvider.createListPreferenceEntity(listId, guestId, Uuids.timeBased().toString())

        ListItemEntity listItemEntity5 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(),
                LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "tcn1234", "1234", "new item",
                1, "note1")

        ListItemEntity listItemEntity6 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(),
                LIST_ITEM_STATE.PENDING.value, ItemType.GENERIC_ITEM.value, "itm3456", null, "genericItem1",
                1, "note2")

        ListItemEntity listItemEntity7 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(),
                LIST_ITEM_STATE.PENDING.value, ItemType.GENERIC_ITEM.value, "itm3456", null, "genericItem1",
                1, "note3")

        ListItemEntity updatedListItemEntity1 = listDataProvider.createListItemEntity(listId, listItemEntity5.itemId,
                listItemEntity5.itemState, listItemEntity5.itemType, listItemEntity5.itemRefId, listItemEntity5.itemTcin,
                listItemEntity5.itemTitle, listItemEntity1.itemReqQty + listItemEntity5.itemReqQty,
                "note1\nitemNote")

        ListItemEntity updatedListItemEntity2 = listDataProvider.createListItemEntity(listId, listItemEntity6.itemId,
                listItemEntity6.itemState, listItemEntity6.itemType, listItemEntity6.itemRefId, listItemEntity6.itemTcin,
                listItemEntity6.itemTitle, listItemEntity3.itemReqQty + listItemEntity6.itemReqQty + listItemEntity7.itemReqQty,
                "note2\nnote3\nitemNote")

        def recordMetadata = GroovyMock(RecordMetadata)

        when:
        def actual = deduplicationManager.updateDuplicateItems(guestId, listId, [listItemEntity1 ,listItemEntity2, listItemEntity3, listItemEntity4], [listItemEntity5, listItemEntity6, listItemEntity7], LIST_ITEM_STATE.PENDING).block()

        then:
        1 * listPreferenceRepository.getListPreference(_,_) >> Mono.just(preUpdateListPreferenceEntity)
        // updating duplicate item
        1 * listRepository.updateListItem(_ as ListItemEntity, null) >> { arguments ->
            final ListItemEntity listItem = arguments[0]
            assert listItem.itemId == listItemEntity5.itemId
            assert listItem.itemReqQty == listItemEntity1.itemReqQty + listItemEntity5.itemReqQty
            Mono.just(updatedListItemEntity1)
        }
        // updating duplicate item
        1 * listRepository.updateListItem(_ as ListItemEntity, null) >> { arguments ->
            final ListItemEntity listItem = arguments[0]
            assert listItem.itemId == listItemEntity6.itemId
            assert listItem.itemReqQty == listItemEntity3.itemReqQty + listItemEntity6.itemReqQty + listItemEntity7.itemReqQty
            Mono.just(updatedListItemEntity2)
        }
        // deleting duplicate items
        1 * listRepository.deleteListItems(_ as List<ListItemEntity>) >> { arguments ->
            final List<ListItemEntity> listItems = arguments[0]
            assert listItems.size() == 1
            assert listItems[0].id == listId
            assert listItems[0].itemId == listItemEntity7.itemId
            Mono.just([listItemEntity7])
        }
        3 * eventPublisher.publishEvent(_,_,_) >> Mono.just(recordMetadata)

        actual.size() == 2
    }
}
