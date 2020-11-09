package com.tgt.lists.atlas.api.service

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.fasterxml.jackson.databind.ObjectMapper
import com.tgt.lists.atlas.api.domain.EventPublisher
import com.tgt.lists.atlas.api.domain.UpdateListItemManager
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.ListItemUpdateRequestTO
import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.api.validator.RefIdValidator
import com.tgt.lists.atlas.kafka.model.UpdateListItemNotifyEvent
import com.tgt.lists.atlas.util.ListDataProvider
import com.tgt.lists.common.components.exception.BadRequestException
import com.tgt.lists.common.components.exception.InternalServerException
import org.apache.kafka.clients.producer.RecordMetadata
import org.jetbrains.annotations.NotNull
import reactor.core.publisher.Mono
import spock.lang.Specification

class UpdateListItemServiceTest extends Specification {

    UpdateListItemService updateListItemService
    UpdateListItemManager updateListItemManager
    EventPublisher eventPublisher
    ListDataProvider listDataProvider
    ListRepository listRepository
    ObjectMapper objectMapper
    String guestId = "1234"
    Long locationId = 1375L

    def setup() {
        eventPublisher = Mock(EventPublisher)
        listRepository = Mock(ListRepository)
        updateListItemManager = new UpdateListItemManager(listRepository, eventPublisher)
        updateListItemService = new UpdateListItemService(listRepository, updateListItemManager)
        listDataProvider = new ListDataProvider()
        objectMapper = new ObjectMapper()
    }

    def "test updateListItem() integrity"() {
        given:
        def listItemUpdateRequest = new ListItemUpdateRequestTO(null, null, "updated item note", null, null, null, null, null, new RefIdValidator() {
            @Override
            boolean requireRefId(@NotNull ItemType itemType, @NotNull ListItemUpdateRequestTO listItemUpdateRequestTO) {
                 if (itemType == ItemType.TCIN && listItemUpdateRequestTO.tcin != null) {
                     return true
                } else if(itemType == ItemType.GENERIC_ITEM && listItemUpdateRequestTO.itemTitle != null) {
                    return true
                } else if(listItemUpdateRequestTO.itemType != null && itemType != listItemUpdateRequestTO.itemType) {
                     return true
                 } else {
                     return false
                 }
            }
        }, null)
        def listId = Uuids.timeBased()
        def itemId = Uuids.timeBased()
        def tcin1 = "1234"
        def tenantRefId1 = listDataProvider.getItemRefId(ItemType.TCIN, tcin1)

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantRefId1, tcin1, "title", 1, "note")

        ListItemEntity updatesListItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantRefId1, tcin1, listItemUpdateRequest.itemTitle, listItemUpdateRequest.requestedQuantity, listItemUpdateRequest.itemNote)


        def recordMetadata = GroovyMock(RecordMetadata)

        when:
        def actual = updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()

        then:
        1 * listRepository.findListItemByItemId(listId, LIST_ITEM_STATE.PENDING.value, itemId) >> Mono.just(listItemEntity)
        // updating duplicate items
        1 * listRepository.updateListItem(_ as ListItemEntity, _) >> { arguments ->
            final ListItemEntity updatedListItem = arguments[0]
            assert updatedListItem.id == listId
            assert updatedListItem.itemNotes == listItemUpdateRequest.itemNote
            Mono.just(updatesListItemEntity)
        }

        1 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _, _) >> Mono.just(recordMetadata)

        actual.listItemId == updatesListItemEntity.itemId
        actual.tcin == updatesListItemEntity.itemTcin
        actual.itemTitle == updatesListItemEntity.itemTitle
        actual.itemNote == updatesListItemEntity.itemNotes
        actual.itemType.value == updatesListItemEntity.itemType
    }

    def "test updateListItem() updating item state"() {
        given:
        def listItemUpdateRequest = new ListItemUpdateRequestTO(null, null, "updated item note", null, null, null, LIST_ITEM_STATE.COMPLETED, null, new RefIdValidator() {
            @Override
            boolean requireRefId(@NotNull ItemType itemType, @NotNull ListItemUpdateRequestTO listItemUpdateRequestTO) {
                if (itemType == ItemType.TCIN && listItemUpdateRequestTO.tcin != null) {
                    return true
                } else if(itemType == ItemType.GENERIC_ITEM && listItemUpdateRequestTO.itemTitle != null) {
                    return true
                } else if(listItemUpdateRequestTO.itemType != null && itemType != listItemUpdateRequestTO.itemType) {
                    return true
                } else {
                    return false
                }
            }
        }, null)
        def listId = Uuids.timeBased()
        def itemId = Uuids.timeBased()
        def tcin1 = "1234"
        def tenantRefId1 = listDataProvider.getItemRefId(ItemType.TCIN, tcin1)

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantRefId1, tcin1, "title", 1, "note")

        ListItemEntity updatesListItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.COMPLETED.value, ItemType.TCIN.value, tenantRefId1, tcin1, listItemUpdateRequest.itemTitle, listItemUpdateRequest.requestedQuantity, listItemUpdateRequest.itemNote)

        def recordMetadata = GroovyMock(RecordMetadata)

        when:
        def actual = updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()

        then:
        1 * listRepository.findListItemByItemId(listId, LIST_ITEM_STATE.PENDING.value, itemId) >> Mono.just(listItemEntity)
        // updating duplicate items
        1 * listRepository.updateListItem(_ as ListItemEntity, _) >> { arguments ->
            final ListItemEntity updatedListItem = arguments[0]
            assert updatedListItem.id == listId
            assert updatedListItem.itemNotes == listItemUpdateRequest.itemNote
            assert updatedListItem.itemState == listItemUpdateRequest.itemState.value
            Mono.just(updatesListItemEntity)
        }

        1 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _, _) >> Mono.just(recordMetadata)

        actual.listItemId == updatesListItemEntity.itemId
        actual.tcin == updatesListItemEntity.itemTcin
        actual.itemTitle == updatesListItemEntity.itemTitle
        actual.itemNote == updatesListItemEntity.itemNotes
        actual.itemType.value == updatesListItemEntity.itemType
    }

    def "test updateListItem() updating tcin and refId for TCIN item"() {
        given:
        def listId = Uuids.timeBased()
        def itemId = Uuids.timeBased()
        def tcin = "1234"
        def updatedTcin = "4567"

        def listItemUpdateRequest = new ListItemUpdateRequestTO(updatedTcin, null, null, null, listDataProvider.getItemRefId(ItemType.TCIN, updatedTcin), null, null, null, new RefIdValidator() {
            @Override
            boolean requireRefId(@NotNull ItemType itemType, @NotNull ListItemUpdateRequestTO listItemUpdateRequestTO) {
                if (itemType == ItemType.TCIN && listItemUpdateRequestTO.tcin != null) {
                    return true
                } else if(itemType == ItemType.GENERIC_ITEM && listItemUpdateRequestTO.itemTitle != null) {
                    return true
                } else if(listItemUpdateRequestTO.itemType != null && itemType != listItemUpdateRequestTO.itemType) {
                    return true
                } else {
                    return false
                }
            }
        }, null)

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, listDataProvider.getItemRefId(ItemType.TCIN, tcin), tcin, "title", 1, "note")

        ListItemEntity updatesListItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, listItemUpdateRequest.itemRefId, listItemUpdateRequest.tcin, "title", 1, "note")

        def recordMetadata = GroovyMock(RecordMetadata)

        when:
        def actual = updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()

        then:
        1 * listRepository.findListItemByItemId(listId, LIST_ITEM_STATE.PENDING.value, itemId) >> Mono.just(listItemEntity)
        // updating duplicate items
        1 * listRepository.updateListItem(_ as ListItemEntity, _) >> { arguments ->
            final ListItemEntity updatedListItem = arguments[0]
            assert updatedListItem.id == listId
            assert updatedListItem.itemTcin == listItemUpdateRequest.tcin
            Mono.just(updatesListItemEntity)
        }

        1 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _, _) >> Mono.just(recordMetadata)

        actual.listItemId == updatesListItemEntity.itemId
        actual.tcin == updatesListItemEntity.itemTcin
    }

    def "test updateListItem() updating tcin without refId in request for TCIN item"() {
        given:
        def listId = Uuids.timeBased()
        def itemId = Uuids.timeBased()
        def tcin = "1234"
        def updatedTcin = "4567"

        def listItemUpdateRequest = new ListItemUpdateRequestTO(updatedTcin, null, null, null, null, null, null, null, new RefIdValidator() {
            @Override
            boolean requireRefId(@NotNull ItemType itemType, @NotNull ListItemUpdateRequestTO listItemUpdateRequestTO) {
                if (itemType == ItemType.TCIN && listItemUpdateRequestTO.tcin != null) {
                    return true
                } else if(itemType == ItemType.GENERIC_ITEM && listItemUpdateRequestTO.itemTitle != null) {
                    return true
                } else if(listItemUpdateRequestTO.itemType != null && itemType != listItemUpdateRequestTO.itemType) {
                    return true
                } else {
                    return false
                }
            }
        }, null)

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, listDataProvider.getItemRefId(ItemType.TCIN, tcin), tcin, "title", 1, "note")

        when:
        updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()

        then:
        1 * listRepository.findListItemByItemId(listId, LIST_ITEM_STATE.PENDING.value, itemId) >> Mono.just(listItemEntity)

        thrown(InternalServerException)
    }

    def "test updateListItem() updating item title for GENERIC ITEM item"() {
        given:
        def listId = Uuids.timeBased()
        def itemId = Uuids.timeBased()
        def updatedTitle = "updated title"

        def listItemUpdateRequest = new ListItemUpdateRequestTO(null, updatedTitle, null, null, listDataProvider.getItemRefId(ItemType.GENERIC_ITEM, updatedTitle), null, null, null, new RefIdValidator() {
            @Override
            boolean requireRefId(@NotNull ItemType itemType, @NotNull ListItemUpdateRequestTO listItemUpdateRequestTO) {
                if (itemType == ItemType.TCIN && listItemUpdateRequestTO.tcin != null) {
                    return true
                } else if(itemType == ItemType.GENERIC_ITEM && listItemUpdateRequestTO.itemTitle != null) {
                    return true
                } else if(listItemUpdateRequestTO.itemType != null && itemType != listItemUpdateRequestTO.itemType) {
                    return true
                } else {
                    return false
                }
            }
        }, null)

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.PENDING.value, ItemType.GENERIC_ITEM.value, listDataProvider.getItemRefId(ItemType.GENERIC_ITEM, "title"), null, "title", 1, "note")

        ListItemEntity updatesListItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.PENDING.value, ItemType.GENERIC_ITEM.value, listItemUpdateRequest.itemRefId, null, listItemUpdateRequest.itemTitle, 1, "note")

        def recordMetadata = GroovyMock(RecordMetadata)

        when:
        def actual = updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()

        then:
        1 * listRepository.findListItemByItemId(listId, LIST_ITEM_STATE.PENDING.value, itemId) >> Mono.just(listItemEntity)
        // updating duplicate items
        1 * listRepository.updateListItem(_ as ListItemEntity, _) >> { arguments ->
            final ListItemEntity updatedListItem = arguments[0]
            assert updatedListItem.id == listId
            assert updatedListItem.itemTitle == listItemUpdateRequest.itemTitle
            Mono.just(updatesListItemEntity)
        }

        1 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _, _) >> Mono.just(recordMetadata)

        actual.listItemId == updatesListItemEntity.itemId
        actual.itemTitle == updatesListItemEntity.itemTitle
    }

    def "test updateListItem() updating item title without refId in request for GENERIC ITEM item"() {
        given:
        def listId = Uuids.timeBased()
        def itemId = Uuids.timeBased()
        def updatedTitle = "updated title"

        def listItemUpdateRequest = new ListItemUpdateRequestTO(null, updatedTitle, null, null, null, null, null, null, new RefIdValidator() {
            @Override
            boolean requireRefId(@NotNull ItemType itemType, @NotNull ListItemUpdateRequestTO listItemUpdateRequestTO) {
                if (itemType == ItemType.TCIN && listItemUpdateRequestTO.tcin != null) {
                    return true
                } else if(itemType == ItemType.GENERIC_ITEM && listItemUpdateRequestTO.itemTitle != null) {
                    return true
                } else if(listItemUpdateRequestTO.itemType != null && itemType != listItemUpdateRequestTO.itemType) {
                    return true
                } else {
                    return false
                }
            }
        }, null)

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.PENDING.value, ItemType.GENERIC_ITEM.value, listDataProvider.getItemRefId(ItemType.GENERIC_ITEM, "title"), null, "title", 1, "note")

        when:
        updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()

        then:
        1 * listRepository.findListItemByItemId(listId, LIST_ITEM_STATE.PENDING.value, itemId) >> Mono.just(listItemEntity)

        thrown(InternalServerException)

    }

    def "test updateListItem() updating item type from GENERIC ITEM to TCIN item"() {
        given:
        def listId = Uuids.timeBased()
        def itemId = Uuids.timeBased()
        def tcin = "1234"

        def listItemUpdateRequest = new ListItemUpdateRequestTO(tcin, null, null, ItemType.TCIN, listDataProvider.getItemRefId(ItemType.TCIN, tcin), null, null, null, new RefIdValidator() {
            @Override
            boolean requireRefId(@NotNull ItemType itemType, @NotNull ListItemUpdateRequestTO listItemUpdateRequestTO) {
                if (itemType == ItemType.TCIN && listItemUpdateRequestTO.tcin != null) {
                    return true
                } else if(itemType == ItemType.GENERIC_ITEM && listItemUpdateRequestTO.itemTitle != null) {
                    return true
                } else if(listItemUpdateRequestTO.itemType != null && itemType != listItemUpdateRequestTO.itemType) {
                    return true
                } else {
                    return false
                }
            }
        }, null)

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.PENDING.value, ItemType.GENERIC_ITEM.value, listDataProvider.getItemRefId(ItemType.GENERIC_ITEM, "title"), null, "title", 1, "note")

        ListItemEntity updatesListItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, listItemUpdateRequest.itemRefId, listItemUpdateRequest.tcin, "title", 1, "note")

        def recordMetadata = GroovyMock(RecordMetadata)

        when:
        def actual = updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()

        then:
        1 * listRepository.findListItemByItemId(listId, LIST_ITEM_STATE.PENDING.value, itemId) >> Mono.just(listItemEntity)
        // updating duplicate items
        1 * listRepository.updateListItem(_ as ListItemEntity, _) >> { arguments ->
            final ListItemEntity updatedListItem = arguments[0]
            assert updatedListItem.id == listId
            assert updatedListItem.itemType == listItemUpdateRequest.itemType.value
            Mono.just(updatesListItemEntity)
        }

        1 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _, _) >> Mono.just(recordMetadata)

        actual.listItemId == updatesListItemEntity.itemId
        actual.itemTitle == updatesListItemEntity.itemTitle
    }

    def "test updateListItem() updating item type from GENERIC ITEM to TCIN item without refId in request"() {
        given:
        def listId = Uuids.timeBased()
        def itemId = Uuids.timeBased()
        def tcin = "1234"

        def listItemUpdateRequest = new ListItemUpdateRequestTO(tcin, null, null, ItemType.TCIN, null, null, null, null, new RefIdValidator() {
            @Override
            boolean requireRefId(@NotNull ItemType itemType, @NotNull ListItemUpdateRequestTO listItemUpdateRequestTO) {
                if (itemType == ItemType.TCIN && listItemUpdateRequestTO.tcin != null) {
                    return true
                } else if(itemType == ItemType.GENERIC_ITEM && listItemUpdateRequestTO.itemTitle != null) {
                    return true
                } else if(listItemUpdateRequestTO.itemType != null && itemType != listItemUpdateRequestTO.itemType) {
                    return true
                } else {
                    return false
                }
            }
        }, null)

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.PENDING.value, ItemType.GENERIC_ITEM.value, listDataProvider.getItemRefId(ItemType.GENERIC_ITEM, "title"), null, "title", 1, "note")

        when:
        updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()

        then:
        1 * listRepository.findListItemByItemId(listId, LIST_ITEM_STATE.PENDING.value, itemId) >> Mono.just(listItemEntity)

        thrown(InternalServerException)
    }

    def "test updateListItem() updating item type from GENERIC ITEM to TCIN item without tcin in request"() {

        when:
        new ListItemUpdateRequestTO(null, null, null, ItemType.TCIN, null, null, null, null, new RefIdValidator() {
            @Override
            boolean requireRefId(@NotNull ItemType itemType, @NotNull ListItemUpdateRequestTO listItemUpdateRequestTO) {
                if (itemType == ItemType.TCIN && listItemUpdateRequestTO.tcin != null) {
                    return true
                } else if(itemType == ItemType.GENERIC_ITEM && listItemUpdateRequestTO.itemTitle != null) {
                    return true
                } else if(listItemUpdateRequestTO.itemType != null && itemType != listItemUpdateRequestTO.itemType) {
                    return true
                } else {
                    return false
                }
            }
        }, null)

        then:
        thrown(BadRequestException)
    }

    def "test updateListItem() updating item type from TCIN to GENERIC_ITEM "() {

        when:
        new ListItemUpdateRequestTO(null, null, null, ItemType.GENERIC_ITEM, null, null, null, null, new RefIdValidator() {
            @Override
            boolean requireRefId(@NotNull ItemType itemType, @NotNull ListItemUpdateRequestTO listItemUpdateRequestTO) {
                if (itemType == ItemType.TCIN && listItemUpdateRequestTO.tcin != null) {
                    return true
                } else if(itemType == ItemType.GENERIC_ITEM && listItemUpdateRequestTO.itemTitle != null) {
                    return true
                } else if(listItemUpdateRequestTO.itemType != null && itemType != listItemUpdateRequestTO.itemType) {
                    return true
                } else {
                    return false
                }
            }
        }, null)

        then:
        thrown(BadRequestException)

    }

    def "test updateListItem() with empty request "() {

        when:
        new ListItemUpdateRequestTO(null, null, null, null, null, null, null, null, new RefIdValidator() {
            @Override
            boolean requireRefId(@NotNull ItemType itemType, @NotNull ListItemUpdateRequestTO listItemUpdateRequestTO) {
                if (itemType == ItemType.TCIN && listItemUpdateRequestTO.tcin != null) {
                    return true
                } else if(itemType == ItemType.GENERIC_ITEM && listItemUpdateRequestTO.itemTitle != null) {
                    return true
                } else return listItemUpdateRequestTO.itemType != null && itemType != listItemUpdateRequestTO.itemType
            }
        }, null)

        then:
        thrown(BadRequestException)
    }
 }