package com.tgt.lists.atlas.api.domain

import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
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
        return listRepository.updateListItem(updatedItem, existingItem).zipWhen {
            val userMetaDataTO = toUserMetaData(it.itemMetadata)
            eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(),
                    UpdateListItemNotifyEvent(guestId, it.id!!, it.itemId!!, it.itemTcin, it.itemTitle, it.itemReqQty,
                            userMetaDataTO?.metadata), listId.toString())
        }.map { it.t1 }
    }
}