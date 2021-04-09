package com.tgt.lists.atlas.api.service

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.fasterxml.jackson.databind.ObjectMapper
import com.tgt.lists.atlas.api.domain.DeduplicationManager
import com.tgt.lists.atlas.api.domain.DeleteListItemsManager
import com.tgt.lists.atlas.api.domain.EventPublisher
import com.tgt.lists.atlas.api.domain.UpdateListItemManager
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.service.transform.list_items.UserItemMetaDataTransformationStep
import com.tgt.lists.atlas.api.transport.ListItemUpdateRequestTO
import com.tgt.lists.atlas.api.type.ItemType
import com.tgt.lists.atlas.api.type.LIST_ITEM_STATE
import com.tgt.lists.atlas.api.type.UserMetaData
import com.tgt.lists.atlas.api.validator.RefIdValidator
import com.tgt.lists.atlas.kafka.model.DeleteListItemNotifyEvent
import com.tgt.lists.atlas.kafka.model.UpdateListItemNotifyEvent
import com.tgt.lists.atlas.util.ListDataProvider
import com.tgt.lists.common.components.exception.BadRequestException
import org.apache.kafka.clients.producer.RecordMetadata
import org.jetbrains.annotations.NotNull
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Instant

class UpdateListItemServiceTest extends Specification {

    UpdateListItemService updateListItemService
    UpdateListItemManager updateListItemManager
    DeleteListItemsManager deleteListItemsManager
    DeduplicationManager deduplicationManager
    EventPublisher eventPublisher
    ListDataProvider listDataProvider
    ListRepository listRepository
    String guestId = "1234"
    Long locationId = 1375L
    def mapper = new ObjectMapper()
    UserItemMetaDataTransformationStep defaultItemMetaDataTransformationStep

    def setup() {
        eventPublisher = Mock(EventPublisher)
        listRepository = Mock(ListRepository)
        deduplicationManager = Mock(DeduplicationManager)
        deleteListItemsManager = new DeleteListItemsManager(listRepository, eventPublisher)
        updateListItemManager = new UpdateListItemManager(listRepository, eventPublisher)
        updateListItemService = new UpdateListItemService(listRepository, updateListItemManager, deleteListItemsManager, deduplicationManager)
        listDataProvider = new ListDataProvider()

        defaultItemMetaDataTransformationStep = new UserItemMetaDataTransformationStep() {
            @Override
            Mono<UserMetaData> execute(@NotNull UserMetaData userItemMetaDataTO) {
                return Mono.just(userItemMetaDataTO)
            }
        }
    }

    def "test updateListItem() integrity"() {
        given:
        def listItemUpdateRequest = new ListItemUpdateRequestTO(null, null, "updated item note", null, null, null, null, null, new RefIdValidator() {
            @Override
            String populateRefIdIfRequired(@NotNull ItemType itemType, @NotNull ListItemUpdateRequestTO listItemUpdateRequestTO) {
                if (itemType == ItemType.TCIN && listItemUpdateRequestTO.tcin != null) {
                     return listDataProvider.getItemRefId(ItemType.TCIN, listItemUpdateRequestTO.tcin)
                } else if(itemType == ItemType.GENERIC_ITEM && listItemUpdateRequestTO.itemTitle != null) {
                    return listDataProvider.getItemRefId(ItemType.GENERIC_ITEM, listItemUpdateRequestTO.itemTitle)
                } else if(listItemUpdateRequestTO.itemType != null && itemType != listItemUpdateRequestTO.itemType) {
                     return populateRefIdIfRequired(listItemUpdateRequestTO.itemType, listItemUpdateRequestTO)
                 } else {
                     return null
                 }
            }
        }, defaultItemMetaDataTransformationStep)
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
        1 * listRepository.findListItemsByListId(listId) >> Flux.just(listItemEntity)
        1 * deduplicationManager.updateDuplicateItems(_,_,_,_,_) >> Mono.just([])
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
        def listItemUpdateRequest = new ListItemUpdateRequestTO(null, null, "updated item note", null, null, LIST_ITEM_STATE.COMPLETED, null, null, new RefIdValidator() {
            @Override
            String populateRefIdIfRequired(@NotNull ItemType itemType, @NotNull ListItemUpdateRequestTO listItemUpdateRequestTO) {
                if (itemType == ItemType.TCIN && listItemUpdateRequestTO.tcin != null) {
                    return listDataProvider.getItemRefId(ItemType.TCIN, listItemUpdateRequestTO.tcin)
                } else if(itemType == ItemType.GENERIC_ITEM && listItemUpdateRequestTO.itemTitle != null) {
                    return listDataProvider.getItemRefId(ItemType.GENERIC_ITEM, listItemUpdateRequestTO.itemTitle)
                } else if(listItemUpdateRequestTO.itemType != null && itemType != listItemUpdateRequestTO.itemType) {
                    return populateRefIdIfRequired(listItemUpdateRequestTO.itemType, listItemUpdateRequestTO)
                } else {
                    return null
                }
            }
        }, defaultItemMetaDataTransformationStep)
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
        1 * listRepository.findListItemsByListId(listId) >> Flux.just(listItemEntity)
        1 * deduplicationManager.updateDuplicateItems(_,_,_,_,_) >> Mono.just([])
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

    def "test updateListItem() updating tcin for TCIN item"() {
        given:
        def listId = Uuids.timeBased()
        def itemId = Uuids.timeBased()
        def tcin = "1234"
        def updatedTcin = "4567"

        def listItemUpdateRequest = new ListItemUpdateRequestTO(updatedTcin, null, null, null, null, null, null, null, new RefIdValidator() {
            @Override
            String populateRefIdIfRequired(@NotNull ItemType itemType, @NotNull ListItemUpdateRequestTO listItemUpdateRequestTO) {
                if (itemType == ItemType.TCIN && listItemUpdateRequestTO.tcin != null) {
                    return listDataProvider.getItemRefId(ItemType.TCIN, listItemUpdateRequestTO.tcin)
                } else if(itemType == ItemType.GENERIC_ITEM && listItemUpdateRequestTO.itemTitle != null) {
                    return listDataProvider.getItemRefId(ItemType.GENERIC_ITEM, listItemUpdateRequestTO.itemTitle)
                } else if(listItemUpdateRequestTO.itemType != null && itemType != listItemUpdateRequestTO.itemType) {
                    return populateRefIdIfRequired(listItemUpdateRequestTO.itemType, listItemUpdateRequestTO)
                } else {
                    return null
                }
            }
        }, defaultItemMetaDataTransformationStep)

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, listDataProvider.getItemRefId(ItemType.TCIN, tcin), tcin, "title", 1, "note")

        ListItemEntity updatesListItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, listDataProvider.getItemRefId(ItemType.TCIN, updatedTcin), listItemUpdateRequest.tcin, "title", 1, "note")

        def recordMetadata = GroovyMock(RecordMetadata)

        when:
        def actual = updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.just(listItemEntity)
        1 * deduplicationManager.updateDuplicateItems(_,_,_,_,_) >> Mono.just([])
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

    def "test updateListItem() updating item title for GENERIC ITEM item"() {
        given:
        def listId = Uuids.timeBased()
        def itemId = Uuids.timeBased()
        def updatedTitle = "updated title"

        def listItemUpdateRequest = new ListItemUpdateRequestTO(null, updatedTitle, null, null, null, null, null, null, new RefIdValidator() {
            @Override
            String populateRefIdIfRequired(@NotNull ItemType itemType, @NotNull ListItemUpdateRequestTO listItemUpdateRequestTO) {
                if (itemType == ItemType.TCIN && listItemUpdateRequestTO.tcin != null) {
                    return listDataProvider.getItemRefId(ItemType.TCIN, listItemUpdateRequestTO.tcin)
                } else if(itemType == ItemType.GENERIC_ITEM && listItemUpdateRequestTO.itemTitle != null) {
                    return listDataProvider.getItemRefId(ItemType.GENERIC_ITEM, listItemUpdateRequestTO.itemTitle)
                } else if(listItemUpdateRequestTO.itemType != null && itemType != listItemUpdateRequestTO.itemType) {
                    return populateRefIdIfRequired(listItemUpdateRequestTO.itemType, listItemUpdateRequestTO)
                } else {
                    return null
                }
            }
        }, defaultItemMetaDataTransformationStep)

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.PENDING.value, ItemType.GENERIC_ITEM.value, listDataProvider.getItemRefId(ItemType.GENERIC_ITEM, "title"), null, "title", 1, "note")

        ListItemEntity updatesListItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.PENDING.value, ItemType.GENERIC_ITEM.value, listDataProvider.getItemRefId(ItemType.GENERIC_ITEM, updatedTitle), null, listItemUpdateRequest.itemTitle, 1, "note")

        def recordMetadata = GroovyMock(RecordMetadata)

        when:
        def actual = updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.just(listItemEntity)
        1 * deduplicationManager.updateDuplicateItems(_,_,_,_,_) >> Mono.just([])
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

    def "test updateListItem() updating item type from GENERIC ITEM to TCIN item"() {
        given:
        def listId = Uuids.timeBased()
        def itemId = Uuids.timeBased()
        def tcin = "1234"

        def listItemUpdateRequest = new ListItemUpdateRequestTO(tcin, null, null, ItemType.TCIN, null, null, null, null, new RefIdValidator() {
            @Override
            String populateRefIdIfRequired(@NotNull ItemType itemType, @NotNull ListItemUpdateRequestTO listItemUpdateRequestTO) {
                if (itemType == ItemType.TCIN && listItemUpdateRequestTO.tcin != null) {
                    return listDataProvider.getItemRefId(ItemType.TCIN, listItemUpdateRequestTO.tcin)
                } else if(itemType == ItemType.GENERIC_ITEM && listItemUpdateRequestTO.itemTitle != null) {
                    return listDataProvider.getItemRefId(ItemType.GENERIC_ITEM, listItemUpdateRequestTO.itemTitle)
                } else if(listItemUpdateRequestTO.itemType != null && itemType != listItemUpdateRequestTO.itemType) {
                    return populateRefIdIfRequired(listItemUpdateRequestTO.itemType, listItemUpdateRequestTO)
                } else {
                    return null
                }
            }
        }, defaultItemMetaDataTransformationStep)

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.PENDING.value, ItemType.GENERIC_ITEM.value, listDataProvider.getItemRefId(ItemType.GENERIC_ITEM, "title"), null, "title", 1, "note")

        ListItemEntity updatesListItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, listDataProvider.getItemRefId(ItemType.TCIN, tcin), listItemUpdateRequest.tcin, "title", 1, "note")

        def recordMetadata = GroovyMock(RecordMetadata)

        when:
        def actual = updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.just(listItemEntity)
        1 * deduplicationManager.updateDuplicateItems(_,_,_,_,_) >> Mono.just([])
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

    def "test updateListItem() updating item type from GENERIC ITEM to TCIN item without tcin in request"() {

        when:
        new ListItemUpdateRequestTO(null, null, null, ItemType.TCIN, null, null, null, null, new RefIdValidator() {
            @Override
            String populateRefIdIfRequired(@NotNull ItemType itemType, @NotNull ListItemUpdateRequestTO listItemUpdateRequestTO) {
                if (itemType == ItemType.TCIN && listItemUpdateRequestTO.tcin != null) {
                    return listDataProvider.getItemRefId(ItemType.TCIN, listItemUpdateRequestTO.tcin)
                } else if(itemType == ItemType.GENERIC_ITEM && listItemUpdateRequestTO.itemTitle != null) {
                    return listDataProvider.getItemRefId(ItemType.GENERIC_ITEM, listItemUpdateRequestTO.itemTitle)
                } else if(listItemUpdateRequestTO.itemType != null && itemType != listItemUpdateRequestTO.itemType) {
                    return populateRefIdIfRequired(listItemUpdateRequestTO.itemType, listItemUpdateRequestTO)
                } else {
                    return null
                }
            }
        }, defaultItemMetaDataTransformationStep)

        then:
        thrown(BadRequestException)
    }

    def "test updateListItem() updating item type from TCIN to GENERIC_ITEM "() {

        when:
        new ListItemUpdateRequestTO(null, null, null, ItemType.GENERIC_ITEM, null, null, null, null, new RefIdValidator() {
            @Override
            String populateRefIdIfRequired(@NotNull ItemType itemType, @NotNull ListItemUpdateRequestTO listItemUpdateRequestTO) {
                if (itemType == ItemType.TCIN && listItemUpdateRequestTO.tcin != null) {
                    return listDataProvider.getItemRefId(ItemType.TCIN, listItemUpdateRequestTO.tcin)
                } else if(itemType == ItemType.GENERIC_ITEM && listItemUpdateRequestTO.itemTitle != null) {
                    return listDataProvider.getItemRefId(ItemType.GENERIC_ITEM, listItemUpdateRequestTO.itemTitle)
                } else if(listItemUpdateRequestTO.itemType != null && itemType != listItemUpdateRequestTO.itemType) {
                    return populateRefIdIfRequired(listItemUpdateRequestTO.itemType, listItemUpdateRequestTO)
                } else {
                    return null
                }
            }
        }, defaultItemMetaDataTransformationStep)

        then:
        thrown(BadRequestException)
    }

    def "test updateListItem() integrity1"() {
        given:
        def listItemUpdateRequest = new ListItemUpdateRequestTO(null, null, "updated item note", null, null, LIST_ITEM_STATE.COMPLETED, null, null, new RefIdValidator() {
            @Override
            String populateRefIdIfRequired(@NotNull ItemType itemType, @NotNull ListItemUpdateRequestTO listItemUpdateRequestTO) {
                if (itemType == ItemType.TCIN && listItemUpdateRequestTO.tcin != null) {
                    return listDataProvider.getItemRefId(ItemType.TCIN, listItemUpdateRequestTO.tcin)
                } else if(itemType == ItemType.GENERIC_ITEM && listItemUpdateRequestTO.itemTitle != null) {
                    return listDataProvider.getItemRefId(ItemType.GENERIC_ITEM, listItemUpdateRequestTO.itemTitle)
                } else if(listItemUpdateRequestTO.itemType != null && itemType != listItemUpdateRequestTO.itemType) {
                    return populateRefIdIfRequired(listItemUpdateRequestTO.itemType, listItemUpdateRequestTO)
                } else {
                    return null
                }
            }
        }, defaultItemMetaDataTransformationStep)
        def listId = Uuids.timeBased()
        def itemId = Uuids.timeBased()
        def tcin1 = "1234"
        def tenantRefId1 = listDataProvider.getItemRefId(ItemType.TCIN, tcin1)

        ListItemEntity listItemEntity1 = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantRefId1, tcin1, "title", 1, "note")
        ListItemEntity listItemEntity2 = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.COMPLETED.value, ItemType.TCIN.value, tenantRefId1, tcin1, "title", 1, "note")

        ListItemEntity dedupedListItemEntity = listDataProvider.createListItemEntity(listId, listItemEntity2.itemId, LIST_ITEM_STATE.COMPLETED.value, ItemType.TCIN.value, tenantRefId1, tcin1, listItemUpdateRequest.itemTitle, listItemEntity1.itemReqQty + listItemEntity2.itemReqQty, listItemEntity1.itemNotes + listItemEntity2.itemNotes)

        def recordMetadata = GroovyMock(RecordMetadata)

        when:
        def actual = updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.just(listItemEntity1, listItemEntity2)
        1 * deduplicationManager.updateDuplicateItems(_,_,_,_,_) >> Mono.just([dedupedListItemEntity])
        1 * listRepository.deleteListItems(_) >> { arguments ->
            final List<ListItemEntity> item = arguments[0]
            assert item.first().id == listId
            assert item.first().itemId == listItemEntity1.itemId
            Mono.just([listItemEntity1])
        }
        1 * eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(), _, _) >> Mono.just(recordMetadata)

        actual.listItemId == dedupedListItemEntity.itemId
        actual.tcin == dedupedListItemEntity.itemTcin
        actual.itemTitle == dedupedListItemEntity.itemTitle
        actual.itemNote == dedupedListItemEntity.itemNotes
        actual.itemType.value == dedupedListItemEntity.itemType
    }

    def "test updateListItem() with existing metadata and metadata transformation step"() {
        given:
        def tcin1 = "1234"
        def oldMetadataString = '{"name":"iPhone 5","brand":"Apple"}'
        def newMetadataString = '{"name":"iPhone 12"}'
        def mergedNewDataString = '{"name":"iPhone 12","brand":"Apple"}'
        def dbMetadata = UserMetaData.@Companion.toUserMetaData(oldMetadataString)
        def newMetadata = UserMetaData.@Companion.toUserMetaData(newMetadataString)

        def testMetaDataTransformationStep = new UserItemMetaDataTransformationStep () {
            @Override
            Mono<UserMetaData> execute(@NotNull UserMetaData existingUserItemMetaDataTO) {
                // merge metadata
                def mergedNewData = UserMetaData.@Companion.toUserMetaData(mergedNewDataString)
                return Mono.just(mergedNewData)
            }
        }

        def listItemUpdateRequest = new ListItemUpdateRequestTO(tcin1, "new title", "updated item note", null, newMetadata, null, null, null, new RefIdValidator() {
            @Override
            String populateRefIdIfRequired(@NotNull ItemType itemType, @NotNull ListItemUpdateRequestTO listItemUpdateRequestTO) {
                if (itemType == ItemType.TCIN && listItemUpdateRequestTO.tcin != null) {
                    return listDataProvider.getItemRefId(ItemType.TCIN, listItemUpdateRequestTO.tcin)
                } else if(itemType == ItemType.GENERIC_ITEM && listItemUpdateRequestTO.itemTitle != null) {
                    return listDataProvider.getItemRefId(ItemType.GENERIC_ITEM, listItemUpdateRequestTO.itemTitle)
                } else if(listItemUpdateRequestTO.itemType != null && itemType != listItemUpdateRequestTO.itemType) {
                    return populateRefIdIfRequired(listItemUpdateRequestTO.itemType, listItemUpdateRequestTO)
                } else {
                    return null
                }
            }
        }, testMetaDataTransformationStep)
        def listId = Uuids.timeBased()
        def itemId = Uuids.timeBased()

        def tenantRefId1 = listDataProvider.getItemRefId(ItemType.TCIN, tcin1)

        ListItemEntity listItemEntity = listDataProvider.createListItemEntity(listId, itemId, LIST_ITEM_STATE.PENDING.value, ItemType.TCIN.value, tenantRefId1, tcin1, "title", 1, "note", UserMetaData.@Companion.toEntityMetadata(dbMetadata), Instant.now(), Instant.now())

        def recordMetadata = GroovyMock(RecordMetadata)

        when:
        def actual = updateListItemService.updateListItem(guestId, locationId, listId, itemId, listItemUpdateRequest).block()

        then:
        1 * listRepository.findListItemsByListId(listId) >> Flux.just(listItemEntity)
        1 * deduplicationManager.updateDuplicateItems(_,_,_,_,_) >> Mono.just([])
        1 * listRepository.updateListItem(_ as ListItemEntity, _) >> { arguments ->
            final ListItemEntity updatedListItem = arguments[0]
            assert updatedListItem.id == listId
            assert updatedListItem.itemNotes == listItemUpdateRequest.itemNote
            assert updatedListItem.itemMetadata == mergedNewDataString
            Mono.just(updatedListItem)
        }

        1 * eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(), _, _) >> Mono.just(recordMetadata)

        actual.listItemId == listItemEntity.itemId
        actual.tcin == listItemEntity.itemTcin
        actual.itemTitle == listItemUpdateRequest.itemTitle
        actual.itemNote == listItemUpdateRequest.itemNote
        actual.itemType.value == listItemEntity.itemType
        UserMetaData.@Companion.toEntityMetadata(actual.metadata) == mergedNewDataString

    }
 }