package com.tgt.lists.atlas.api.domain

import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.type.ItemType
import com.tgt.lists.atlas.api.type.LIST_ITEM_STATE
import com.tgt.lists.atlas.api.type.UserMetaData.Companion.toUserMetaData
import com.tgt.lists.atlas.kafka.model.UpdateListItemNotifyEvent
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateListItemManager(
    @Inject private val listRepository: ListRepository,
    @Inject private val eventPublisher: EventPublisher
) {

    private val logger = KotlinLogging.logger { UpdateListItemManager::class.java.name }

    fun updateListItem(
        guestId: String,
        listId: UUID,
        updatedItem: ListItemEntity,
        existingItem: ListItemEntity? = null // existingItem entity not passed when its called from Deduplication Manager
    ): Mono<ListItemEntity> {
        logger.debug("[updateListItem] Updating list item")
        return listRepository.updateListItem(updatedItem, existingItem)
                .zipWhen {
                    val userMetaDataTO = toUserMetaData(it.itemMetadata)
                    eventPublisher.publishEvent(
                            UpdateListItemNotifyEvent.getEventType(),
                            UpdateListItemNotifyEvent(guestId = guestId,
                                    listId = it.id!!,
                                    itemId = it.itemId!!,
                                    itemState = LIST_ITEM_STATE.values().first { itemState -> itemState.value == it.itemState!! },
                                    itemType = ItemType.values().first { itemType -> itemType.value == it.itemType!! },
                                    tcin = it.itemTcin,
                                    itemTitle = it.itemTitle,
                                    channel = it.itemChannel,
                                    subChannel = it.itemSubchannel,
                                    itemRequestedQuantity = it.itemReqQty,
                                    userItemMetaDataTO = userMetaDataTO?.metadata),
                            listId.toString())
        }.map { it.t1 }
    }
}