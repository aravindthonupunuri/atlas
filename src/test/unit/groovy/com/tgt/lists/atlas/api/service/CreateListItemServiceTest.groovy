package com.tgt.lists.atlas.api.service

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.api.domain.*
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.ListItemRequestTO
import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.api.util.UnitOfMeasure
import com.tgt.lists.atlas.kafka.model.CreateListItemNotifyEvent
import com.tgt.lists.atlas.util.CartDataProvider
import com.tgt.lists.atlas.util.ListDataProvider
import com.tgt.lists.atlas.util.TestListChannel
import org.apache.kafka.clients.producer.RecordMetadata
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

class CreateListItemServiceTest extends Specification {
    
    EventPublisher eventPublisher
    DeduplicationManager deduplicationManager
    AddListItemsManager addMultiItemsManager
    InsertListItemsManager insertListItemsManager
    DeleteListItemsManager deleteListItemsManager
    CreateListItemService createListItemService
    ListRepository listRepository
    CartDataProvider cartDataProvider
    ListDataProvider listDataProvider
    String guestId = "1234"

    def setup() {
        listRepository = Mock(ListRepository)
        eventPublisher = Mock(EventPublisher)
        deleteListItemsManager = new DeleteListItemsManager(listRepository, eventPublisher)
        insertListItemsManager = new InsertListItemsManager(listRepository, eventPublisher)
        deduplicationManager = new DeduplicationManager(listRepository, insertListItemsManager, deleteListItemsManager, true, 10, 10, false)
        addMultiItemsManager = new AddListItemsManager(deduplicationManager, insertListItemsManager)
        createListItemService = new CreateListItemService(addMultiItemsManager)
        cartDataProvider = new CartDataProvider()
        listDataProvider = new ListDataProvider()
    }

    def "test createListItem() integrity"() {
        given:
        def tcin1 = "1234"
        def tenantrefId1 = cartDataProvider.getTenantRefId(ItemType.TCIN, tcin1)
        def listId = UUID.randomUUID()

        def listItemRequest = new ListItemRequestTO(ItemType.TCIN, tenantrefId1, TestListChannel.WEB.toString(), tcin1, null,
            "test item", null, UnitOfMeasure.EACHES, null)

        ListItemEntity newListItemEntity = listDataProvider.createListItemEntity(listId, Uuids.timeBased() , LIST_ITEM_STATE.PENDING.name(), ItemType.TCIN.name(), tenantrefId1, tcin1,  listItemRequest.itemTitle, 1, listItemRequest.itemNote)

        when:
        def actual = createListItemService.createListItem(guestId, listId, 1357L, listItemRequest).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.empty()
        1 * listRepository.saveListItems(_) >> Mono.just([newListItemEntity])
        1 * eventPublisher.publishEvent(CreateListItemNotifyEvent.eventType, _, listId.toString()) >> Mono.just(GroovyMock(RecordMetadata))

        actual.listItemId == newListItemEntity.itemId
        actual.tcin == newListItemEntity.itemTcin
        actual.itemTitle == newListItemEntity.itemTitle
        actual.itemNote == newListItemEntity.itemNotes
        actual.price == null
        actual.listPrice == null
        actual.images == null
        actual.itemType.name() == newListItemEntity.itemType
    }
}
