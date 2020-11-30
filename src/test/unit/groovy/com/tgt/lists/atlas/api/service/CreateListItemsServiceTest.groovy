package com.tgt.lists.atlas.api.service

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.api.domain.*
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListPreferenceEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.ListItemRequestTO
import com.tgt.lists.atlas.api.type.ItemType
import com.tgt.lists.atlas.api.type.LIST_ITEM_STATE
import com.tgt.lists.atlas.api.type.UnitOfMeasure
import com.tgt.lists.atlas.util.ListDataProvider
import com.tgt.lists.atlas.util.TestListChannel
import com.tgt.lists.common.components.exception.BadRequestException
import org.apache.kafka.clients.producer.RecordMetadata
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

class CreateListItemsServiceTest extends Specification {

    EventPublisher eventPublisher
    DeduplicationManager deduplicationManager
    DeleteListItemsManager deleteListItemsManager
    CreateListItemsManager createListItemsManager
    UpdateListItemManager updateListItemManager
    CreateListItemsService createListItemsService
    ListRepository listRepository
    ListDataProvider listDataProvider

    String guestId = "1234"

    def setup() {
        eventPublisher = Mock(EventPublisher)
        listRepository = Mock(ListRepository)
        listDataProvider = new ListDataProvider()
        deleteListItemsManager = new DeleteListItemsManager(listRepository, eventPublisher)
        updateListItemManager = new UpdateListItemManager(listRepository, eventPublisher)
        deduplicationManager = new DeduplicationManager(updateListItemManager, deleteListItemsManager, listDataProvider.getConfiguration(100, 10, 10, true, true, false))
        createListItemsManager = new CreateListItemsManager(deduplicationManager, listRepository, eventPublisher)
        createListItemsService = new CreateListItemsService(createListItemsManager)

    }

    def "test createListItem() integrity"() {
        given:
        def listId = Uuids.timeBased()
        def tcin1 = "1234"
        def tenantrefId1 = listDataProvider.getItemRefId(ItemType.TCIN, tcin1)
        def tcin2 = "5678"
        def tenantrefId2 = listDataProvider.getItemRefId(ItemType.TCIN, tcin2)
        def tcin3 = "1000"
        def tenantrefId3 = listDataProvider.getItemRefId(ItemType.TCIN, tcin3)
        def tcin4 = "9999"
        def tenantrefId4 = listDataProvider.getItemRefId(ItemType.TCIN, tcin4)
        def tcin5 = "1111"
        def tenantrefId5 = listDataProvider.getItemRefId(ItemType.TCIN, tcin5)

        def listItemRequest1 = new ListItemRequestTO(ItemType.TCIN, tenantrefId1, TestListChannel.WEB.toString(), null, tcin1, null,
                "test item", null, null, UnitOfMeasure.EACHES, null, null)
        def listItemRequest2 = new ListItemRequestTO(ItemType.TCIN, tenantrefId2, TestListChannel.WEB.toString(), null, tcin2, null,
                "test item", null, null, UnitOfMeasure.EACHES, null, null)
        def listItemRequest3 = new ListItemRequestTO(ItemType.TCIN, tenantrefId3, TestListChannel.WEB.toString(), null, tcin3, null,
                "test item", null, null, UnitOfMeasure.EACHES, null, null)
        def listItemRequest4 = new ListItemRequestTO(ItemType.TCIN, tenantrefId4, TestListChannel.WEB.toString(), null, tcin4, null,
                "test item", null, null, UnitOfMeasure.EACHES, null, null)

        ListItemEntity listItemEntity1 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantrefId1, tcin1, null, 2, "notes1")
        ListItemEntity listItemEntity2 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantrefId1, tcin1, null, 3, "notes1")
        ListItemEntity listItemEntity3 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantrefId2, tcin2, null, 1, "notes1")
        ListItemEntity listItemEntity4 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantrefId2, tcin2, null, 2, "notes1")
        ListItemEntity listItemEntity5 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantrefId2, tcin2, null, 3, "notes1")
        ListItemEntity listItemEntity6 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantrefId5, tcin5, null, 1, "notes1")
        ListItemEntity listItemEntity7 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantrefId5, tcin5, null, 1, "notes1")

        ListItemEntity updatedListItemEntity1 = listDataProvider.createListItemEntity(listId, listItemEntity1.itemId , LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantrefId1, tcin1, null,6, "notes1\nnotes2")
        ListItemEntity updatedListItemEntity2 = listDataProvider.createListItemEntity(listId, listItemEntity3.itemId, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantrefId2, tcin2, null,7, "notes3\nnotes4\nnotes5")

        ListItemEntity newListItemEntity1 = listDataProvider.createListItemEntity(listId, Uuids.timeBased() , LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantrefId3, tcin3,  null, 1, "newitemNote1")
        ListItemEntity newListItemEntity2 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantrefId3, tcin4,  null, 1, "newitemNote2")

        List<ListItemRequestTO> itemsToAdd = [listItemRequest1, listItemRequest2, listItemRequest3, listItemRequest4]

        ListPreferenceEntity preUpdateListPreferenceEntity = listDataProvider.createListPreferenceEntity(listId, guestId, Uuids.timeBased().toString())

        def recordMetadata = GroovyMock(RecordMetadata)
        when:
        def actual = createListItemsService.createListItems(guestId, listId, 1357L, itemsToAdd)
                .block()
        then:
        1 * listRepository.findListItemsByListIdAndItemState(listId, LIST_ITEM_STATE.PENDING.value) >> Flux.just(listItemEntity1, listItemEntity2, listItemEntity3, listItemEntity4, listItemEntity5, listItemEntity6, listItemEntity7)

        // updating duplicate item
        1 * listRepository.updateListItem(_ as ListItemEntity, null) >> { arguments ->
            final ListItemEntity listItem = arguments[0]
            assert listItem.itemId == listItemEntity1.itemId
            assert listItem.itemReqQty == listItemEntity1.itemReqQty + listItemEntity2.itemReqQty + 1
            Mono.just(updatedListItemEntity1)
        }
        // updating duplicate item
        1 * listRepository.updateListItem(_ as ListItemEntity, null) >> { arguments ->
            final ListItemEntity listItem = arguments[0]
            assert listItem.itemId == listItemEntity3.itemId
            assert listItem.itemReqQty == listItemEntity3.itemReqQty + listItemEntity4.itemReqQty + listItemEntity5.itemReqQty + 1
            Mono.just(updatedListItemEntity2)
        }

        // deleting duplicate items
        1 * listRepository.deleteListItems(_ as List<ListItemEntity>) >> { arguments ->
            final List<ListItemEntity> items = arguments[0]
            assert items.size() == 3
            Mono.just([listItemEntity2, listItemEntity4, listItemEntity5])
        }
        // inserting new items
        1 * listRepository.saveListItems(_ as List<ListItemEntity>) >> { arguments ->
            final List<ListItemEntity> items = arguments[0]
            assert items.size() == 2
            Mono.just([newListItemEntity1, newListItemEntity2])
        }
        // events published
        7 * eventPublisher.publishEvent(_,_,_) >> Mono.just(recordMetadata)
        actual.items.size() == 4
    }

    def "test createListItem() with duplicate items in request"() {
        given:
        def listId = Uuids.timeBased()
        def tcin1 = "1234"
        def tcinTenantRefId1 = listDataProvider.getItemRefId(ItemType.TCIN, tcin1)
        def gItem1 = "item2"
        def gItemTenantrefId1 = listDataProvider.getItemRefId(ItemType.GENERIC_ITEM, gItem1)

        def listItemRequest1 = new ListItemRequestTO(ItemType.TCIN, tcinTenantRefId1, TestListChannel.WEB.toString(), null, tcin1, null,
                "test item", null, null, UnitOfMeasure.EACHES, null, null)

        def listItemRequest2 = new ListItemRequestTO(ItemType.GENERIC_ITEM, gItemTenantrefId1, TestListChannel.WEB.toString(), null, null, gItem1,
                "test item", null, null, UnitOfMeasure.EACHES, null, null)

        def listItemRequest3 = new ListItemRequestTO(ItemType.GENERIC_ITEM, gItemTenantrefId1, TestListChannel.WEB.toString(), null, null, gItem1,
                "test item", null, null, UnitOfMeasure.EACHES, null, null)

        def itemsToAdd = [listItemRequest1, listItemRequest2, listItemRequest3]

        ListItemEntity newListItemEntity1 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tcinTenantRefId1, tcin1,  null, 1, "notes1")
        ListItemEntity newListItemEntity2 = listDataProvider.createListItemEntity(listId, Uuids.timeBased(), LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, gItemTenantrefId1, null,  gItem1, 1, "notes1")

        def recordMetadata = GroovyMock(RecordMetadata)

        when:
        createListItemsService.createListItems(guestId, listId, 1357L, itemsToAdd).block()

        then:
        1 * listRepository.findListItemsByListIdAndItemState(listId, LIST_ITEM_STATE.PENDING.value) >> Flux.empty()
        // inserting new items
        1 * listRepository.saveListItems(_ as List<ListItemEntity>) >> { arguments ->
            final List<ListItemEntity> items = arguments[0]
            assert items.size() == 2
            Mono.just([newListItemEntity1, newListItemEntity2])
        }
        // events published
        2 * eventPublisher.publishEvent(_,_,_) >> Mono.just(recordMetadata)
    }

    def "test ListItemRequestTO tcin item with exception"() {
        given:
        def tcin1 = "1234"
        def tenantrefId1 = listDataProvider.getItemRefId(ItemType.TCIN, tcin1)

        when:
        new ListItemRequestTO(ItemType.TCIN, tenantrefId1, TestListChannel.WEB.toString(), null, tcin1, "title",
                "test item", null, null, UnitOfMeasure.EACHES, null, null)
        then:
        thrown(BadRequestException)
    }

    def "test ListItemRequestTO generic item with exception"() {
        given:
        def tcin1 = "1234"
        def tenantrefId1 = listDataProvider.getItemRefId(ItemType.TCIN, tcin1)

        when:
        new ListItemRequestTO(ItemType.GENERIC_ITEM, tenantrefId1, TestListChannel.WEB.toString(), null, tcin1, "title",
                "test item", null, null, UnitOfMeasure.EACHES, null, null)
        then:
        thrown(BadRequestException)
    }

    def "test ListItemRequestTO offer item with exception"() {
        given:
        def tcin1 = "1234"
        def tenantrefId1 = listDataProvider.getItemRefId(ItemType.TCIN, tcin1)

        when:
        new ListItemRequestTO(ItemType.OFFER, tenantrefId1, TestListChannel.WEB.toString(), null, tcin1, "title",
                "test item", null, null, UnitOfMeasure.EACHES, null, null)
        then:
        thrown(BadRequestException)
    }
}
