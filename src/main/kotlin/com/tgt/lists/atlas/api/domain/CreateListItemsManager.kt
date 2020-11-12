package com.tgt.lists.atlas.api.domain

import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.ListItemRequestTO
import com.tgt.lists.atlas.api.transport.mapper.ListItemMapper
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.kafka.model.CreateListItemNotifyEvent
import mu.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreateListItemsManager(
    @Inject private val deduplicationManager: DeduplicationManager,
    @Inject private val listRepository: ListRepository,
    @Inject private val eventPublisher: EventPublisher
) {

    private val logger = KotlinLogging.logger { CreateListItemsManager::class.java.name }

    /**
     *
     * Implements the functionality to add multiple list items. Items already existing in the list
     * are updated and new items are added to the list
     *
     */
    fun createListItems(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        listId: UUID,
        listItemState: LIST_ITEM_STATE,
        newItems: List<ListItemRequestTO>
    ): Mono<List<ListItemEntity>> {

        return listRepository.findListItemsByListIdAndItemState(listId, listItemState.value).collectList().flatMap {
            val itemsToAdd = newItems.map { it.itemRefId to ListItemMapper.toNewListItemEntity(listId, it) }.toMap().values.toList() // Filtering out duplicate items in the request

            deduplicationManager.updateDuplicateItems(guestId = guestId, listId = listId, items = itemsToAdd,
                    existingItems = it, itemState = listItemState).flatMap { updatedItems ->

                val itemsToCreate = itemsToAdd.filter { item ->
                    !updatedItems.parallelStream().anyMatch { it.itemRefId == item.itemRefId }
                }

                val finalItems = arrayListOf<ListItemEntity>()

                if (itemsToCreate.isNullOrEmpty()) {
                    logger.debug("[createListItems] guestId: $guestId listId:$listId, No list items to create")
                    finalItems.addAll(updatedItems)
                    Mono.just(finalItems)
                } else {
                    createListItems(guestId, listId, itemsToCreate).map {
                        finalItems.addAll(it)
                        finalItems.addAll(updatedItems)
                        finalItems
                    }
                }
            }
        }
    }

    fun createListItems(
        guestId: String,
        listId: UUID,
        listItems: List<ListItemEntity>
    ): Mono<List<ListItemEntity>> {
        logger.debug("[createListItems] guestId: $guestId listId:$listId")

        return listRepository.saveListItems(listItems).zipWhen { items ->
            Flux.fromIterable(items.asIterable()).flatMap {
                val userMetaDataTO = ListItemMapper.getUserItemMetaDataFromMetadataMap(it.itemMetadata)
                eventPublisher.publishEvent(CreateListItemNotifyEvent.getEventType(),
                        CreateListItemNotifyEvent(guestId, it.id!!, it.itemId!!,
                                LIST_ITEM_STATE.values().first { itemState -> itemState.value == it.itemState!! },
                                it.itemTcin, it.itemTitle, it.itemChannel, it.itemReqQty, userMetaDataTO?.userMetaData),
                            listId.toString()) }.collectList() }.map { it.t1 }
        }
}
