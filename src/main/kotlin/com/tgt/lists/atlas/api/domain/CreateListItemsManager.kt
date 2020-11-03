package com.tgt.lists.atlas.api.domain

import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.ListItemRequestTO
import com.tgt.lists.atlas.api.transport.mapper.ListItemMapper
import com.tgt.lists.atlas.api.transport.mapper.ListMapper
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.kafka.model.CreateListItemNotifyEvent
import com.tgt.lists.atlas.kafka.model.UpdateListItemNotifyEvent
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
        val newItemsMap = newItems.map {
            it.itemRefId to it
        }.toMap()

        return deduplicationManager.updateDuplicateItems(guestId, listId, newItemsMap, listItemState)
                .flatMap { addNewItems(guestId, listId, newItemsMap, it.first, it.second) }
    }

    /**
     *
     * Implements the functionality to add multiple list items after completing the deduplication process.
     *
     */
    private fun addNewItems(
        guestId: String,
        listId: UUID,
        newItemsMap: Map<String, ListItemRequestTO>,
        updatedItems: List<ListItemEntity>,
        duplicateItemsMap: MutableMap<String, List<ListItemEntity>>
    ): Mono<List<ListItemEntity>> {
        val itemsToCreate = newItemsMap.filter { !duplicateItemsMap.contains(it.key) }
                .map { ListItemMapper.toNewListItemEntity(listId, it.value) }
        val listItems = arrayListOf<ListItemEntity>()

        if (itemsToCreate.isNullOrEmpty()) {
            logger.debug("From addNewItems(), No items to add")
            listItems.addAll(updatedItems)
            return Mono.just(listItems)
        }

        return addListItems(guestId, listId, itemsToCreate, false)
                .map {
                    listItems.addAll(it)
                    listItems.addAll(updatedItems)
                    listItems
                }
    }

    fun addListItems(
        guestId: String,
        listId: UUID,
        listItems: List<ListItemEntity>,
        update: Boolean // flag to differentiate is the call is made from add items or update items
    ): Mono<List<ListItemEntity>> {
        return if (listItems.isNullOrEmpty()) {
            logger.debug("[createListItems] guestId: $guestId listId:$listId, No list items to insert")
            Mono.just(emptyList())
        } else {
            logger.debug("[createListItems] guestId: $guestId listId:$listId")
            return listRepository.saveListItems(listItems).zipWhen { items ->
                Flux.fromIterable(items.asIterable()).flatMap {
                    val userMetaDataTO = ListMapper.getUserMetaDataFromMetadataMap(it.itemMetadata)
                    if (update) {
                        eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(),
                                UpdateListItemNotifyEvent(guestId, it.id!!, it.itemId!!, it.itemTcin, it.itemTitle,
                                        it.itemReqQty, userMetaDataTO?.userMetaData), listId.toString())
                    } else {
                        eventPublisher.publishEvent(CreateListItemNotifyEvent.getEventType(),
                                CreateListItemNotifyEvent(guestId, it.id!!, it.itemId!!,
                                        LIST_ITEM_STATE.values().first { itemState -> itemState.value == it.itemState!! },
                                        it.itemTcin, it.itemTitle, it.itemChannel, it.itemReqQty, userMetaDataTO?.userMetaData),
                                listId.toString())
                    }
                }.collectList()
            }.map { it.t1 }
        }
    }
}
