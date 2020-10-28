package com.tgt.lists.atlas.api.service

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.util.CartDataProvider
import com.tgt.lists.atlas.util.ListDataProvider
import reactor.core.publisher.Mono
import spock.lang.Specification

class GetListItemServiceTest extends Specification {

    GetListItemService getListItemService
    CartDataProvider cartDataProvider
    ListRepository listRepository
    ListDataProvider listDataProvider

    String guestId = "1234"
    Long locationId= 1375

    def setup() {
        listRepository = Mock(ListRepository)
        getListItemService = new GetListItemService(listRepository)
        cartDataProvider = new CartDataProvider()
        listDataProvider = new ListDataProvider()
    }

    def "test getListItemService() integrity"() {
        given:
        def listId = Uuids.timeBased()
        def listItemId = Uuids.timeBased()
        def tcin = "1234"
        def tenantRefId = cartDataProvider.getItemRefId(ItemType.TCIN, tcin)

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, listItemId, LIST_ITEM_STATE.PENDING.name(), ItemType.TCIN.name(), tenantRefId, tcin, null, 1, "notes1")

        when:
        def actual = getListItemService.getListItem(guestId, locationId, listId, listItemId).block()

        then:
        1 * listRepository.findListItemByItemId(listId, LIST_ITEM_STATE.PENDING.name(), listItemId) >> Mono.just(listItemEntity)

        actual.listItemId == listItemEntity.itemId
        actual.tcin == listItemEntity.itemTcin
        actual.itemTitle == listItemEntity.itemTitle
        actual.itemNote == listItemEntity.itemNotes
        actual.itemType.toString() == listItemEntity.itemType
    }

    def "test getListItemService() when item in completed state"() {
        given:
        def listId = Uuids.timeBased()
        def listItemId = Uuids.timeBased()
        def tcin = "1234"
        def tenantRefId = cartDataProvider.getItemRefId(ItemType.TCIN, tcin)

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, listItemId, LIST_ITEM_STATE.PENDING.name(), ItemType.TCIN.name(), tenantRefId, tcin, null, 1, "notes1")

        when:
        def actual = getListItemService.getListItem(guestId, locationId, listId, listItemId).block()

        then:
        1 * listRepository.findListItemByItemId(listId, LIST_ITEM_STATE.PENDING.name(), listItemId) >> Mono.empty()
        1 * listRepository.findListItemByItemId(listId, LIST_ITEM_STATE.COMPLETED.name(), listItemId) >> Mono.just(listItemEntity)


        actual.listItemId == listItemEntity.itemId
        actual.tcin == listItemEntity.itemTcin
        actual.itemTitle == listItemEntity.itemTitle
        actual.itemNote == listItemEntity.itemNotes
        actual.itemType.toString() == listItemEntity.itemType
    }

    def "test getListItemService() when item not found in list"() {
        given:
        def listId = Uuids.timeBased()
        def listItemId = Uuids.timeBased()

        when:
        def actual = getListItemService.getListItem(guestId, locationId, listId, listItemId).block()

        then:
        1 * listRepository.findListItemByItemId(listId, LIST_ITEM_STATE.PENDING.name(), listItemId) >> Mono.empty()
        1 * listRepository.findListItemByItemId(listId, LIST_ITEM_STATE.COMPLETED.name(), listItemId) >> Mono.empty()

        actual == null
    }

    def "test getListItemService() exception getting item in pending state"() {
        given:
        def listId = Uuids.timeBased()
        def listItemId = Uuids.timeBased()

        when:
        getListItemService.getListItem(guestId, locationId, listId, listItemId).block()

        then:
        1 * listRepository.findListItemByItemId(listId, LIST_ITEM_STATE.PENDING.name(), listItemId) >> Mono.error(new RuntimeException("some error"))

        thrown(RuntimeException)
    }

    def "test getListItemService() exception getting item in completed state"() {
        given:
        def listId = Uuids.timeBased()
        def listItemId = Uuids.timeBased()

        when:
        getListItemService.getListItem(guestId, locationId, listId, listItemId).block()

        then:
        1 * listRepository.findListItemByItemId(listId, LIST_ITEM_STATE.PENDING.name(), listItemId) >> Mono.empty()
        1 * listRepository.findListItemByItemId(listId, LIST_ITEM_STATE.COMPLETED.name(), listItemId) >> Mono.error(new RuntimeException("some error"))

        thrown(RuntimeException)
    }
}
