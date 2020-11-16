package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.domain.ListPreferenceSortOrderManager
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListPreferenceEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListPreferenceRepository
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.EditItemSortOrderRequestTO
import com.tgt.lists.atlas.api.util.Direction
import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.util.ListDataProvider
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

class ListItemSortOrderServiceTest extends Specification {

    ListPreferenceRepository listPreferenceRepository
    ListRepository listRepository
    ListPreferenceSortOrderManager listPreferenceSortOrderManager
    ListItemSortOrderService listItemSortOrderService
    ListDataProvider listDataProvider
    String guestId = "1234"

    def setup() {
        listDataProvider = new ListDataProvider()
        listPreferenceRepository = Mock(ListPreferenceRepository)
        listRepository = Mock(ListRepository)
        listPreferenceSortOrderManager = new ListPreferenceSortOrderManager(listPreferenceRepository, listRepository)
        listItemSortOrderService = new ListItemSortOrderService(listPreferenceSortOrderManager)
    }

    def "Test saveListItemSortOrder() when list item status is pending"() {
        given:
        UUID listId = UUID.randomUUID()
        UUID itemId = UUID.randomUUID()
        def listPreference = new ListPreferenceEntity(listId, guestId, itemId.toString())

        when:
        def actual = listItemSortOrderService.saveListItemSortOrder(guestId, listId, itemId, LIST_ITEM_STATE.PENDING).block()

        then:
        actual

        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.empty()
        1 * listPreferenceRepository.saveListPreference(listPreference) >> Mono.just(listPreference)
    }

    def "Test saveListItemSortOrder() when list item status is completed"() {
        given:
        UUID listId = UUID.randomUUID()
        UUID itemId = UUID.randomUUID()

        when:
        def actual = listItemSortOrderService.saveListItemSortOrder(guestId, listId, itemId, LIST_ITEM_STATE.COMPLETED).block()

        then:
        actual
    }

    def "Test deleteListItemSortOrder() when list item status is pending"() {
        given:
        UUID listId = UUID.randomUUID()
        UUID itemId = UUID.randomUUID()
        UUID itemId1 = UUID.randomUUID()
        def tcin = "1234"
        def tenantRefId = listDataProvider.getItemRefId(ItemType.TCIN, tcin)

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantRefId, tcin, null, 1, "notes1")
        def preList = new ListPreferenceEntity(listId, guestId, itemId1.toString() + "," + itemId.toString())
        def postList = new ListPreferenceEntity(listId, guestId, itemId1.toString())

        when:
        def actual = listItemSortOrderService.deleteListItemSortOrder(guestId, listId, [listItemEntity]).block()

        then:
        actual

        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preList)
        1 * listPreferenceRepository.saveListPreference(postList) >> Mono.just(postList)
    }

    def "Test deleteListItemSortOrder() when deleteListItems size is 0"() {
        given:
        UUID listId = UUID.randomUUID()

        when:
        def actual = listItemSortOrderService.deleteListItemSortOrder(guestId, listId, []).block()

        then:
        actual
    }

    def "Test deleteListItemSortOrder() when list item status is pending and item to be deleted is not found"() {
        given:
        UUID listId = UUID.randomUUID()
        UUID itemId = UUID.randomUUID()
        UUID itemId1 = UUID.randomUUID()
        def tcin = "1234"
        def tenantRefId = listDataProvider.getItemRefId(ItemType.TCIN, tcin)

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantRefId, tcin, null, 1, "notes1")
        def preList = new ListPreferenceEntity(listId, guestId, itemId1.toString())

        when:
        def actual = listItemSortOrderService.deleteListItemSortOrder(guestId, listId, [listItemEntity]).block()

        then:
        !actual

        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preList)
    }

    def "Test editListItemSortOrder() happy path"() {
        given:
        UUID listId = UUID.randomUUID()
        UUID primaryItemId = UUID.randomUUID()
        UUID secondaryItemId = UUID.randomUUID()
        def editItemSortOrderRequestTO = new EditItemSortOrderRequestTO(guestId, listId, primaryItemId, secondaryItemId, Direction.ABOVE)
        def preList = new ListPreferenceEntity(listId, guestId, secondaryItemId.toString() + "," + primaryItemId.toString())
        def postList = new ListPreferenceEntity(listId, guestId, primaryItemId.toString() + "," + secondaryItemId.toString())

        def listItemExtEntities = listDataProvider.createListItemExtEntities(listId, [primaryItemId,secondaryItemId], guestId)

        when:
        def actual = listItemSortOrderService.editListItemSortOrder(editItemSortOrderRequestTO).block()

        then:
        actual

        1 * listRepository.findListAndItemsByListIdAndItemState(listId,_) >> Flux.fromIterable(listItemExtEntities)
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preList)
        1 * listPreferenceRepository.saveListPreference(postList) >> Mono.just(postList)
    }

    def "Test editListItemSortOrder() when secondary item id does not exist"() {
        given:
        UUID listId = UUID.randomUUID()
        UUID primaryItemId = UUID.randomUUID()
        UUID secondaryItemId = UUID.randomUUID()
        def editItemSortOrderRequestTO = new EditItemSortOrderRequestTO(guestId, listId, primaryItemId, secondaryItemId, Direction.ABOVE)
        def preList = new ListPreferenceEntity(listId, guestId, primaryItemId.toString())
        def postList = new ListPreferenceEntity(listId, guestId, primaryItemId.toString() + "," + secondaryItemId.toString())

        def listItemExtEntities = listDataProvider.createListItemExtEntities(listId, [primaryItemId,secondaryItemId], guestId)

        when:
        def actual = listItemSortOrderService.editListItemSortOrder(editItemSortOrderRequestTO).block()

        then:
        actual

        1 * listRepository.findListAndItemsByListIdAndItemState(listId,_) >> Flux.fromIterable(listItemExtEntities)
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preList)
        1 * listPreferenceRepository.saveListPreference(postList) >> Mono.just(postList)
    }

    def "Test editListItemSortOrder() when primary item id does not exist"() {
        given:
        UUID listId = UUID.randomUUID()
        UUID primaryItemId = UUID.randomUUID()
        UUID secondaryItemId = UUID.randomUUID()
        def editItemSortOrderRequestTO = new EditItemSortOrderRequestTO(guestId, listId, primaryItemId, secondaryItemId, Direction.ABOVE)
        def preList = new ListPreferenceEntity(listId, guestId, secondaryItemId.toString())
        def postList = new ListPreferenceEntity(listId, guestId, primaryItemId.toString() + "," + secondaryItemId.toString())

        def listItemExtEntities = listDataProvider.createListItemExtEntities(listId, [primaryItemId,secondaryItemId], guestId)

        when:
        def actual = listItemSortOrderService.editListItemSortOrder(editItemSortOrderRequestTO).block()

        then:
        actual

        1 * listRepository.findListAndItemsByListIdAndItemState(listId,_) >> Flux.fromIterable(listItemExtEntities)
        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preList)
        1 * listPreferenceRepository.saveListPreference(postList) >> Mono.just(postList)
    }

}