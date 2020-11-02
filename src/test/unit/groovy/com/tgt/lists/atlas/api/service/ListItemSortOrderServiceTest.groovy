package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.domain.ListItemSortOrderManager
import com.tgt.lists.atlas.api.domain.model.entity.ListPreferenceEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListPreferenceRepository
import com.tgt.lists.atlas.api.transport.EditItemSortOrderRequestTO
import com.tgt.lists.atlas.api.util.Direction
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.kafka.model.MultiDeleteListItem
import reactor.core.publisher.Mono
import spock.lang.Specification

class ListItemSortOrderServiceTest extends Specification {

    ListPreferenceRepository listPreferenceRepository
    ListItemSortOrderManager listItemSortOrderManager
    ListItemSortOrderService listItemSortOrderService
    String guestId = "1234"

    def setup() {
        listPreferenceRepository = Mock(ListPreferenceRepository)
        listItemSortOrderManager = new ListItemSortOrderManager(listPreferenceRepository)
        listItemSortOrderService = new ListItemSortOrderService(listItemSortOrderManager)
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
        def multiDeleteListItem = new MultiDeleteListItem(itemId, null, null, null, LIST_ITEM_STATE.PENDING, null)
        def preList = new ListPreferenceEntity(listId, guestId, itemId1.toString() + "," + itemId.toString())
        def postList = new ListPreferenceEntity(listId, guestId, itemId1.toString())

        when:
        def actual = listItemSortOrderService.deleteListItemSortOrder(guestId, listId, [multiDeleteListItem]).block()

        then:
        actual

        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preList)
        1 * listPreferenceRepository.saveListPreference(postList) >> Mono.just(postList)
    }

    def "Test deleteListItemSortOrder() when list item status is null"() {
        given:
        UUID listId = UUID.randomUUID()
        UUID itemId = UUID.randomUUID()
        def multiDeleteListItem = new MultiDeleteListItem(itemId, null, null, null, null, null)

        when:
        def actual = listItemSortOrderService.deleteListItemSortOrder(guestId, listId, [multiDeleteListItem]).block()

        then:
        actual
    }


    def "Test deleteListItemSortOrder() when list item status is pending and item to be deleted is not found"() {
        given:
        UUID listId = UUID.randomUUID()
        UUID itemId = UUID.randomUUID()
        UUID itemId1 = UUID.randomUUID()
        def multiDeleteListItem = new MultiDeleteListItem(itemId, null, null, null, LIST_ITEM_STATE.PENDING, null)
        def preList = new ListPreferenceEntity(listId, guestId, itemId1.toString())

        when:
        def actual = listItemSortOrderService.deleteListItemSortOrder(guestId, listId, [multiDeleteListItem]).block()

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

        when:
        def actual = listItemSortOrderService.editListItemSortOrder(editItemSortOrderRequestTO).block()

        then:
        actual

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

        when:
        def actual = listItemSortOrderService.editListItemSortOrder(editItemSortOrderRequestTO).block()

        then:
        actual

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

        when:
        def actual = listItemSortOrderService.editListItemSortOrder(editItemSortOrderRequestTO).block()

        then:
        actual

        1 * listPreferenceRepository.getListPreference(listId, guestId) >> Mono.just(preList)
        1 * listPreferenceRepository.saveListPreference(postList) >> Mono.just(postList)
    }

}