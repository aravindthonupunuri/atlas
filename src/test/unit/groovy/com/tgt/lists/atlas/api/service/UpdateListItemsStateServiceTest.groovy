package com.tgt.lists.atlas.api.service

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.github.dockerjava.api.exception.BadRequestException
import com.tgt.lists.atlas.api.domain.EventPublisher
import com.tgt.lists.atlas.api.domain.UpdateListItemManager
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.kafka.model.UpdateListItemNotifyEvent
import com.tgt.lists.atlas.util.CartDataProvider
import com.tgt.lists.atlas.util.ListDataProvider
import org.apache.kafka.clients.producer.RecordMetadata
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

class UpdateListItemsStateServiceTest extends Specification {

    UpdateListItemsStateService updateListItemsStateService
    UpdateListItemManager updateListItemManager
    EventPublisher eventPublisher
    CartDataProvider cartDataProvider
    ListDataProvider listDataProvider
    ListRepository listRepository
    String guestId = "1234"
    Long locationId = 1375L

    def setup() {
        eventPublisher = Mock(EventPublisher)
        listRepository = Mock(ListRepository)
        updateListItemManager = new UpdateListItemManager(listRepository, eventPublisher)
        updateListItemsStateService = new UpdateListItemsStateService(listRepository, updateListItemManager)
        cartDataProvider = new CartDataProvider()
        listDataProvider = new ListDataProvider()
    }

    def "test updateListItem() integrity"() {
        given:
        def listId = Uuids.timeBased()
        def itemId1 = Uuids.timeBased()
        def itemId2 = Uuids.timeBased()
        def tcin1 = "1234"
        def tenantRefId1 = listDataProvider.getItemRefId(ItemType.TCIN, tcin1)
        def tcin2 = "4567"
        def tenantRefId2 = listDataProvider.getItemRefId(ItemType.TCIN, tcin2)

        ListItemEntity listItemEntity1 = listDataProvider.createListItemEntity(listId, itemId1, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantRefId1, tcin1, "title", 1, "note")
        ListItemEntity listItemEntity2 = listDataProvider.createListItemEntity(listId, itemId2, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantRefId2, tcin2, "title", 1, "note")


        ListItemEntity updatesListItemEntity1 = listDataProvider.createListItemEntity(listId, itemId2, LIST_ITEM_STATE.COMPLETED.value, ItemType.TCIN.value, tenantRefId2, tcin2, "title", 1, "note")
        ListItemEntity updatesListItemEntity2 = listDataProvider.createListItemEntity(listId, itemId2, LIST_ITEM_STATE.COMPLETED.value, ItemType.TCIN.value, tenantRefId2, tcin2, "title", 1, "note")

        def recordMetadata = GroovyMock(RecordMetadata)

        when:
        def actual = updateListItemsStateService.updateListItemsState(guestId, locationId, listId, LIST_ITEM_STATE.COMPLETED, [itemId1, itemId2]).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.just(listItemEntity1, listItemEntity2)
        // updating item1
        1 * listRepository.updateListItem(_ as ListItemEntity, _) >> { arguments ->
            final ListItemEntity updatedlistItem = arguments[0]
            assert updatedlistItem.itemState == LIST_ITEM_STATE.COMPLETED.value
            Mono.just(updatesListItemEntity1)
        }
        // updating item2
        1 * listRepository.updateListItem(_ as ListItemEntity, _) >> { arguments ->
            final ListItemEntity updatedlistItem = arguments[0]
            assert updatedlistItem.itemState == LIST_ITEM_STATE.COMPLETED.value
            Mono.just(updatesListItemEntity2)
        }

        2 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _, _) >> Mono.just(recordMetadata)

        actual.successListItemIds.size() == 2
    }

    def "test updateListItem() having invalid and already updated items"() {
        given:
        def listId = Uuids.timeBased()
        def itemId1 = Uuids.timeBased()
        def itemId2 = Uuids.timeBased()
        def itemId3 = Uuids.timeBased()
        def tcin1 = "1234"
        def tenantRefId1 = listDataProvider.getItemRefId(ItemType.TCIN, tcin1)
        def tcin2 = "4567"
        def tenantRefId2 = listDataProvider.getItemRefId(ItemType.TCIN, tcin2)

        ListItemEntity listItemEntity1 = listDataProvider.createListItemEntity(listId, itemId1, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantRefId1, tcin1, "title", 1, "note")
        ListItemEntity listItemEntity2 = listDataProvider.createListItemEntity(listId, itemId2, LIST_ITEM_STATE.COMPLETED.value, ItemType.TCIN.value, tenantRefId2, tcin2, "title", 1, "note")

        ListItemEntity updatesListItemEntity1 = listDataProvider.createListItemEntity(listId, itemId1, LIST_ITEM_STATE.COMPLETED.value, ItemType.TCIN.value, tenantRefId1, tcin1, "title", 1, "note")

        def recordMetadata = GroovyMock(RecordMetadata)

        when:
        def actual = updateListItemsStateService.updateListItemsState(guestId, locationId, listId, LIST_ITEM_STATE.COMPLETED, [itemId1, itemId2, itemId3]).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.just(listItemEntity1, listItemEntity2)
        // updating item1
        1 * listRepository.updateListItem(_ as ListItemEntity, _) >> { arguments ->
            final ListItemEntity updatedlistItem = arguments[0]
            assert updatedlistItem.itemState == LIST_ITEM_STATE.COMPLETED.value
            Mono.just(updatesListItemEntity1)
        }
        1 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _, _) >> Mono.just(recordMetadata)

        actual.successListItemIds.size() == 2
        actual.failedListItemIds.size() == 1
    }

    def "test updateListItem() having not items to update"() {
        given:
        def listId = Uuids.timeBased()
        def itemId1 = Uuids.timeBased()
        def itemId2 = Uuids.timeBased()
        def itemId3 = Uuids.timeBased()

        when:
        def actual = updateListItemsStateService.updateListItemsState(guestId, locationId, listId, LIST_ITEM_STATE.COMPLETED, [itemId1, itemId2, itemId3]).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.empty()

        actual.failedListItemIds.size() == 3
    }

    def "test updateListItem() with exception"() {
        given:
        def listId = Uuids.timeBased()
        def itemId1 = Uuids.timeBased()
        def itemId2 = Uuids.timeBased()
        def tcin1 = "1234"
        def tenantRefId1 = listDataProvider.getItemRefId(ItemType.TCIN, tcin1)
        def tcin2 = "4567"
        def tenantRefId2 = listDataProvider.getItemRefId(ItemType.TCIN, tcin2)

        ListItemEntity listItemEntity1 = listDataProvider.createListItemEntity(listId, itemId1, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantRefId1, tcin1, "title", 1, "note")
        ListItemEntity listItemEntity2 = listDataProvider.createListItemEntity(listId, itemId2, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantRefId2, tcin2, "title", 1, "note")

        ListItemEntity updatesListItemEntity1 = listDataProvider.createListItemEntity(listId, itemId2, LIST_ITEM_STATE.COMPLETED.value, ItemType.TCIN.value, tenantRefId2, tcin2, "title", 1, "note")

        def recordMetadata = GroovyMock(RecordMetadata)

        when:
        updateListItemsStateService.updateListItemsState(guestId, locationId, listId, LIST_ITEM_STATE.COMPLETED, [itemId1, itemId2]).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.just(listItemEntity1, listItemEntity2)
        // updating item1
        1 * listRepository.updateListItem(_ as ListItemEntity, _) >> { arguments ->
            final ListItemEntity updatedlistItem = arguments[0]
            assert updatedlistItem.itemState == LIST_ITEM_STATE.COMPLETED.value
            Mono.just(updatesListItemEntity1)
        }
        1 * listRepository.updateListItem(_ as ListItemEntity, _) >> Mono.error(new BadRequestException("some exception"))
        1 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _, _) >> Mono.just(recordMetadata)

        thrown(BadRequestException)
    }
 }