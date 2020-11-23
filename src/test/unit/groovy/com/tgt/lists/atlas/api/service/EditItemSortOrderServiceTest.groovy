package com.tgt.lists.atlas.api.service

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.EditItemSortOrderRequestTO
import com.tgt.lists.atlas.api.type.Direction
import com.tgt.lists.atlas.api.type.ItemType
import com.tgt.lists.atlas.api.type.LIST_ITEM_STATE
import com.tgt.lists.atlas.util.ListDataProvider
import com.tgt.lists.common.components.exception.BadRequestException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

class EditItemSortOrderServiceTest extends Specification {

    ListItemSortOrderService listItemSortOrderService
    ListRepository listRepository
    ListDataProvider listDataProvider
    EditItemSortOrderService editItemSortOrderService
    def guestId = "1234"

    def setup() {
        listDataProvider = new ListDataProvider()
        listRepository = Mock(ListRepository)
        listItemSortOrderService = Mock(ListItemSortOrderService)
        editItemSortOrderService = Mock(EditItemSortOrderService)
        editItemSortOrderService = new EditItemSortOrderService(listRepository, listItemSortOrderService)
    }

    def "Test editItemPosition() when primary and secondary item id are same"() {
        given:
        UUID listId = Uuids.timeBased()
        UUID primaryItemId = Uuids.timeBased()
        UUID secondaryItemId = primaryItemId
        Direction direction = Direction.ABOVE
        def item1Tcin = "1234"

        EditItemSortOrderRequestTO editItemSortOrderRequestTO = new EditItemSortOrderRequestTO(guestId, listId, primaryItemId, secondaryItemId, direction)

        ListItemEntity itemEntity1 = listDataProvider.createListItemEntity(listId, primaryItemId, LIST_ITEM_STATE.PENDING.value,
        ItemType.TCIN.value, listDataProvider.getItemRefId(ItemType.TCIN, item1Tcin), item1Tcin, null, null, null)

        ListItemEntity itemEntity2 = listDataProvider.createListItemEntity(listId, primaryItemId, LIST_ITEM_STATE.PENDING.value,
                ItemType.TCIN.value, listDataProvider.getItemRefId(ItemType.TCIN, item1Tcin), item1Tcin, null, null, null)

        when:
        def actual = editItemSortOrderService.editItemPosition(editItemSortOrderRequestTO).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.just(itemEntity1, itemEntity2)
        
        actual
    }

    def "Test editItemPosition() when primary and secondary item id are different"() {
        given:
        UUID listId = UUID.randomUUID()
        UUID primaryItemId = UUID.randomUUID()
        UUID secondaryItemId = UUID.randomUUID()
        Direction direction = Direction.ABOVE
        def item1Tcin = "1234"
        def item2Tcin = "5431"

        EditItemSortOrderRequestTO editItemSortOrderRequestTO = new EditItemSortOrderRequestTO(guestId, listId, primaryItemId, secondaryItemId, direction)

        ListItemEntity itemEntity1 = listDataProvider.createListItemEntity(listId, primaryItemId, LIST_ITEM_STATE.PENDING.value,
                ItemType.TCIN.value, listDataProvider.getItemRefId(ItemType.TCIN, item1Tcin), item1Tcin, null, null, null)

        ListItemEntity itemEntity2 = listDataProvider.createListItemEntity(listId, secondaryItemId, LIST_ITEM_STATE.PENDING.value,
                ItemType.TCIN.value, listDataProvider.getItemRefId(ItemType.TCIN, item2Tcin), item2Tcin, null, null, null)

        when:
        def actual = editItemSortOrderService.editItemPosition(editItemSortOrderRequestTO).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.just(itemEntity1, itemEntity2)
        1 * listItemSortOrderService.editListItemSortOrder(editItemSortOrderRequestTO) >> Mono.just(true)

        actual
    }

    def "test editListPosition() when unauthorized items ids are passed"() {
        given:
        UUID listId = UUID.randomUUID()
        UUID primaryItemId = UUID.randomUUID()
        UUID secondaryItemId = UUID.randomUUID()
        Direction direction = Direction.ABOVE
        def item1Tcin = "1234"

        EditItemSortOrderRequestTO editItemSortOrderRequestTO = new EditItemSortOrderRequestTO(guestId, listId, primaryItemId, secondaryItemId, direction)

        ListItemEntity itemEntity1 = listDataProvider.createListItemEntity(listId, primaryItemId, LIST_ITEM_STATE.PENDING.value,
                ItemType.TCIN.value, listDataProvider.getItemRefId(ItemType.TCIN, item1Tcin), item1Tcin, null, null, null)

        when:
        editItemSortOrderService.editItemPosition(editItemSortOrderRequestTO).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.just(itemEntity1)

        thrown BadRequestException

    }
}
