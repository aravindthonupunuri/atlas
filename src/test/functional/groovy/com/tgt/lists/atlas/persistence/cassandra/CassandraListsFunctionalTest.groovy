package com.tgt.lists.atlas.persistence.cassandra

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.BaseFunctionalTest
import com.tgt.lists.atlas.api.domain.model.entity.GuestListEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListItemExtEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.util.ListDataProvider
import io.micronaut.test.annotation.MicronautTest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Stepwise
import spock.lang.Unroll

import javax.inject.Inject
import java.util.stream.Collectors

@MicronautTest
@Stepwise
class CassandraListsFunctionalTest extends BaseFunctionalTest {

    static final Logger logger = LoggerFactory.getLogger(CassandraListsFunctionalTest)

    @Inject
    ListRepository listsRepository

    @Shared
    ListDataProvider dataProvider = new ListDataProvider()

    @Shared
    List<UUID> listIds = [Uuids.timeBased(), Uuids.timeBased(), Uuids.timeBased(), Uuids.timeBased()]

    @Shared
    List<UUID> listItemIds = [Uuids.timeBased(), Uuids.timeBased(), Uuids.timeBased(), Uuids.timeBased()]

    @Shared
    List<String> guestIds = ["126890567", "126890567", "126890567", "531244530"]

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
        ListEntity listEntity = dataProvider.createListEntity(listId, listTitle, listType, listSubtype, guestId, listMarker)

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

    @Unroll
    def "test add weekly list items #itemRefId"() {
        given:
        ListItemEntity listItemEntity = dataProvider.createListItemEntity(listId, itemId, itemState, itemType, itemRefId)

        when:
        listsRepository.saveListItem(listItemEntity).block()

        then:
        notThrown(Throwable)

        where:
        listId       | itemId         | itemState   | itemType  | itemRefId
        listIds[0]   | listItemIds[0] | "PENDING"   | "tcin"    | "52829076"
        listIds[0]   | listItemIds[1] | "PENDING"   | "tcin"    | "15833332"
        listIds[0]   | listItemIds[2] | "COMPLETED" | "generic" | "coffee"
        listIds[0]   | listItemIds[3] | "COMPLETED" | "offer"   | "100000"
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

    def "test get list items by listId"() {
        given:
        def listId = listIds[0]

        when:
        List<ListItemEntity> listItemEntities = listsRepository.findListItemsByListId(listId).collect(Collectors.toList()).block()

        then:
        listItemEntities.size() == 4
    }

    def "test get list items by itemState PENDING"() {
        given:
        def listId = listIds[0]

        when:
        List<ListItemEntity> listItemEntities = listsRepository.findListItemsByListIdAndItemState(listId, "PENDING").collect(Collectors.toList()).block()

        then:
        listItemEntities.size() == 2
    }

    def "test get list item by id"() {
        given:
        def listId = listIds[0]
        def itemState = "PENDING"
        def itemId = listItemIds[0]

        when:
        ListItemExtEntity listItemExtEntity = listsRepository.findListItemByItemId(listId, itemState, itemId).block()

        then:
        listItemExtEntity != null
        listItemExtEntity.title == "weekly"
        listItemExtEntity.itemRefId == "52829076"
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

