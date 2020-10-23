package com.tgt.lists.atlas.api.domain

import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.mapper.ListMapper
import com.tgt.lists.atlas.kafka.model.CreateListItemNotifyEvent
import com.tgt.lists.atlas.kafka.model.UpdateListItemNotifyEvent
import mu.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InsertListItemsManager(
    @Inject private val listRepository: ListRepository,
    @Inject private val eventPublisher: EventPublisher
) {

    private val logger = KotlinLogging.logger { InsertListItemsManager::class.java.name }

    fun insertListItems(
        guestId: String,
        listId: UUID,
        listItems: List<ListItemEntity>,
        update: Boolean // flag to differentiate is the call is made from add items or update items
    ): Mono<List<ListItemEntity>> {
        return if (listItems.isNullOrEmpty()) {
            logger.debug("[insertListItems] guestId: $guestId listId:$listId, No list items to insert")
            Mono.just(emptyList())
        } else {
            logger.debug("[insertListItems] guestId: $guestId listId:$listId")
            return listRepository.saveListItems(listItems).zipWhen { items ->
                Flux.fromIterable(items.asIterable()).flatMap {
                    val userMetaDataTO = ListMapper.getUserMetaDataFromMetadataMap(it.itemMetadata)
                    if (update) {
                        eventPublisher.publishEvent(UpdateListItemNotifyEvent.getEventType(),
                                UpdateListItemNotifyEvent(guestId, it.id!!, it.itemId!!, it.itemTcin, it.itemTitle,
                                        it.itemReqQty, null, userMetaDataTO?.userMetaData), listId.toString())
                    } else {
                        eventPublisher.publishEvent(CreateListItemNotifyEvent.getEventType(),
                                CreateListItemNotifyEvent(guestId, it.id!!, it.itemId!!, it.itemTcin, it.itemTitle,
                                        it.itemChannel, it.itemReqQty, null, userMetaDataTO?.userMetaData),
                                listId.toString())
                    }
                }.collectList()
            }.map { it.t1 }
        }
    }
}