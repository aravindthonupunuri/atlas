package com.tgt.lists.atlas.persistence.cassandra

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.BaseFunctionalTest
import com.tgt.lists.atlas.api.domain.model.entity.GuestListEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListItemExtEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.type.ItemType
import com.tgt.lists.atlas.api.type.LIST_ITEM_STATE
import com.tgt.lists.atlas.util.ListDataProvider
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Stepwise
import spock.lang.Unroll

import javax.inject.Inject
import java.time.Instant

@MicronautTest
@Stepwise
class CassandraListsFunctionalTest extends BaseFunctionalTest {

    @Inject
    ListRepository listsRepository

    @Shared
    ListDataProvider dataProvider = new ListDataProvider()

    @Shared
    List<UUID> listIds = [Uuids.timeBased(), Uuids.timeBased(), Uuids.timeBased(), Uuids.timeBased()]

    @Shared
    List<UUID> listItemIds = [Uuids.timeBased(), Uuids.timeBased(), Uuids.timeBased(), Uuids.timeBased()]

    @Shared
    List<String> guestIds = ["126890560", "126890560", "126890560", "531244530"]

    @Shared
    List<String> listTypes = ["shopping", "shopping", "registry", "favorites"]

    @Shared
    List<String> listSubtypes = ["s", "s", "r", "f"]

    @Shared
    List<String> listMarkers = ["d", "n", "n", "n"]

    def setup() {
    }

    @Unroll
    def "test create new list #listTitle"() {
        given:
        ListEntity listEntity = dataProvider.createListEntity(listId, listTitle, listType, listSubtype, guestId, listMarker, Instant.now(), Instant.now())

        when:
        listsRepository.saveList(listEntity).block()

        then:
        notThrown(Throwable)

        where:
        listId       | listTitle    | listType     | listSubtype     | guestId     | listMarker
        listIds[0]   | "weekly"     | listTypes[0] | listSubtypes[0] | guestIds[0] | listMarkers[0]
        listIds[1]   | "home"       | listTypes[1] | listSubtypes[1] | guestIds[1] | listMarkers[1]
        listIds[2]   | "bday"       | listTypes[2] | listSubtypes[2] | guestIds[2] | listMarkers[2]
        listIds[3]   | "essentials" | listTypes[3] | listSubtypes[3] | guestIds[3] | listMarkers[3]
    }

    def "test get list items from a list with no items"() {
        when:
        List<ListItemEntity> listItems = listsRepository.findListItemsByListId(listIds[0]).collectList().block()

        then:
        listItems.isEmpty()
    }

    def "test get lists by ids"() {
        when:
        List<ListEntity> listEntity  = listsRepository.findLists([listIds[0], listIds[1], listIds[2], listIds[3]] as Set).collectList().block()

        then:
        listEntity.size() == 4
    }

    def "test add weekly list item"() {
        given:
        ListItemEntity listItemEntity = dataProvider.createListItemEntity(listIds[0], listItemIds[0], LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value,  "52829076", "1234", null, null, null)

        when:
        listsRepository.saveListItems([listItemEntity]).block()

        then:
        notThrown(Throwable)
    }

    def "test add list item to non existing list"() {
        given:
        ListItemEntity listItemEntity = dataProvider.createListItemEntity(Uuids.timeBased(), listItemIds[0], LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value,  "52829076", "1234", null, null, null)

        when:
        listsRepository.saveListItems([listItemEntity]).block()

        then:
        notThrown(Throwable)
    }

    def "test add weekly list items by batch"() {
        given:
        ListItemEntity listItemEntity1 = dataProvider.createListItemEntity(listIds[0], listItemIds[1], LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "15833332", "4567", null, null, null)
        ListItemEntity listItemEntity2 = dataProvider.createListItemEntity(listIds[0], listItemIds[2], LIST_ITEM_STATE.COMPLETED.value, ItemType.GENERIC_ITEM.value, "coffee", null, "title", null, null)
        ListItemEntity listItemEntity3 = dataProvider.createListItemEntity(listIds[0], listItemIds[3], LIST_ITEM_STATE.COMPLETED.value, ItemType.OFFER.value, "100000", null, null, null, null)


        when:
        listsRepository.saveListItems([listItemEntity1, listItemEntity2, listItemEntity3]).block()

        then:
        notThrown(Throwable)
    }

    def "test findGuestLists"() {
        when:
        List<ListEntity> listEntity  = listsRepository.findGuestLists("126890560", "shopping").block()

        then:
        listEntity.size() == 2
    }

    def "test get list by id"() {
        given:
        def listId = listIds[0]

        when:
        ListEntity listEntity = listsRepository.findListById(listId).block()

        then:
        listEntity != null
        listEntity.title == "weekly"
    }

    def "test get list by listId which is not present "() {
        when:
        ListEntity listEntity = listsRepository.findListById(Uuids.timeBased()).block()

        then:
        listEntity == null
    }

    def "test get list items by listId"() {
        given:
        def listId = listIds[0]

        when:
        List<ListItemEntity> listItemEntities = listsRepository.findListItemsByListId(listId).collectList().block()

        then:
        listItemEntities.size() == 4
    }

    def "test get list items by listId which is not present"() {
        when:
        List<ListItemEntity> listItemEntities = listsRepository.findListItemsByListId(Uuids.timeBased()).collectList().block()

        then:
        listItemEntities.size() == 0
    }

    def "test get list and items by listId"() {
        given:
        def listId = listIds[0]

        when:
        List<ListItemExtEntity> listItemExtEntities = listsRepository.findListAndItemsByListId(listId).collectList().block()

        then:
        listItemExtEntities.size() == 4
    }

    def "test get list and items by listId, item state"() {
        given:
        def listId = listIds[0]

        when:
        List<ListItemExtEntity> listItemExtEntities = listsRepository.findListAndItemsByListIdAndItemState(listId, LIST_ITEM_STATE.PENDING.value).collectList().block()

        then:
        listItemExtEntities.size() == 2
    }

    def "test get list items by itemState PENDING"() {
        given:
        def listId = listIds[0]

        when:
        List<ListItemEntity> listItemEntities = listsRepository.findListItemsByListIdAndItemState(listId, LIST_ITEM_STATE.PENDING.value).collectList().block()

        then:
        listItemEntities.size() == 2
    }

    def "test get list item by id"() {
        given:
        def listId = listIds[0]
        def itemId = listItemIds[0]

        when:
        ListItemEntity listItemEntity = listsRepository.findListItemByItemId(listId, itemId).block()

        then:
        listItemEntity != null
        listItemEntity.itemRefId == "52829076"
    }

    def "test get list item by non existing item id"() {
        given:
        def listId = listIds[0]

        when:
        ListItemEntity listItemEntity = listsRepository.findListItemByItemId(listId, Uuids.timeBased()).block()

        then:
        listItemEntity == null
    }

    def "test get guest listid by list marker"() {
        given:
        def guestId = guestIds[0]
        def listType = listTypes[0]
        def listSubtype = listSubtypes[0]
        def listMarker = listMarkers[0]

        when:
        GuestListEntity guestListEntity = listsRepository.findGuestListByMarker(guestId, listType, listSubtype, listMarker).block()

        then:
        guestListEntity != null
        guestListEntity.id == listIds[0]
    }

    def "test get guest listid by guestId and list tyep"() {
        given:
        def guestId = guestIds[0]
        def listType = listTypes[0]

        when:
        List<GuestListEntity> guestListEntities = listsRepository.findGuestListsByGuestId(guestId, listType).collectList().block()

        then:
        guestListEntities != null
        guestListEntities.size() > 0
    }

    def "test update listEntity"() {
        given:
        def listId = listIds[0]
        def updateDescription = "modified description"
        def updateNotes = "modified Notes"
        ListEntity existingListEntity = listsRepository.findListById(listId).block()
        ListEntity updateListEntity = new ListEntity(listId,
                existingListEntity.title,
                existingListEntity.type,
                existingListEntity.subtype,
                existingListEntity.guestId,
                updateDescription,
                existingListEntity.channel,
                existingListEntity.subchannel,
                existingListEntity.marker,
                existingListEntity.location,
                updateNotes,
                existingListEntity.state,
                null,
                null,
                null,
                existingListEntity.createdAt,
                null,
                Boolean.FALSE
        )

        when:
        ListEntity listEntity = listsRepository.updateList(existingListEntity, updateListEntity).block()

        then:
        listEntity != null
        listEntity.title == "weekly"
        listEntity.description == updateDescription
        listEntity.notes == updateNotes
    }

    def "test update listEntity as well guestEntity"() {
        given:
        def listId = listIds[0]
        def updateDescription = "modified description"
        def updateNotes = "modified Notes"
        ListEntity existingListEntity = listsRepository.findListById(listId).block()
        ListEntity updateListEntity = new ListEntity(listId,
                existingListEntity.title,
                existingListEntity.type,
                existingListEntity.subtype,
                existingListEntity.guestId,
                updateDescription,
                existingListEntity.channel,
                existingListEntity.subchannel,
                "update Marker",
                existingListEntity.location,
                updateNotes,
                existingListEntity.state,
                null,
                null,
                null,
                existingListEntity.createdAt,
                null,
                Boolean.FALSE
        )

        when:
        ListEntity listEntity = listsRepository.updateList(existingListEntity, updateListEntity).block()

        then:
        listEntity != null
        listEntity.title == "weekly"
        listEntity.description == updateDescription
        listEntity.notes == updateNotes
    }

    def "test delete weekly list items by batch"() {
        given:
        ListItemEntity listItemEntity1 = dataProvider.createListItemEntity(listIds[0], listItemIds[1], LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, "15833332", "4567", null, null, null)
        ListItemEntity listItemEntity2 = dataProvider.createListItemEntity(listIds[0], listItemIds[2], LIST_ITEM_STATE.COMPLETED.value, ItemType.GENERIC_ITEM.value, "coffee", null, "title", null, null)
        ListItemEntity listItemEntity3 = dataProvider.createListItemEntity(listIds[0], listItemIds[3], LIST_ITEM_STATE.COMPLETED.value, ItemType.OFFER.value, "100000", null, null, null, null)

        when:
        List<ListItemEntity> deletedListItems = listsRepository.deleteListItems([listItemEntity1, listItemEntity2, listItemEntity3]).block()

        then:
        deletedListItems.size() == 3
    }

    def "test delete weekly list item"() {
        given:
        ListItemEntity listItemEntity = dataProvider.createListItemEntity(listIds[0], listItemIds[0], LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value,  "52829076", "1234", null, null, null)

        when:
        List<ListItemEntity> deletedListItems = listsRepository.deleteListItems([listItemEntity]).block()

        then:
        deletedListItems.size() == 1
    }

    def "test if weekly list items got deleted"() {
        given:
        def listId = listIds[0]

        when:
        List<ListItemEntity> listItems = listsRepository.findListItemsByListId(listId).collectList().block()

        then:
        listItems.isEmpty()
    }

    def "test delete weekly list"() {
        given:
        def listId = listIds[0]

        when:
        ListEntity listEntity1 = listsRepository.findListById(listId).block()
        ListEntity listEntity2 = listsRepository.deleteList(listEntity1).block()

        then:
        listEntity2 != null
        listEntity2.title == "weekly"
    }

    def "test if weekly list is deleted"() {
        given:
        def listId = listIds[0]

        when:
        ListEntity listEntity = listsRepository.findListById(listId).block()

        then:
        listEntity == null
    }
}

