package com.tgt.lists.atlas.api.domain

import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.mapper.ListMapper
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import com.tgt.lists.atlas.kafka.model.DeleteListItemNotifyEvent
import com.tgt.lists.atlas.kafka.model.MultiDeleteListItem
import mu.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeleteListItemsManager(
    @Inject private val listRepository: ListRepository,
    @Inject private val eventPublisher: EventPublisher
) {

    private val logger = KotlinLogging.logger { DeleteListItemsManager::class.java.name }

    // TODO Delete the item from the sort order if any
    fun deleteListItems(
        guestId: String,
        listId: UUID,
        listItems: List<ListItemEntity>
    ): Mono<List<ListItemEntity>> {
        return if (listItems.isNullOrEmpty()) {
            logger.debug("[deleteListItems] guestId: $guestId listId:$listId, No list items to delete")
            Mono.just(emptyList())
        } else {
            logger.debug("[deleteListItems] guestId: $guestId listId:$listId")
            listRepository.deleteListItems(listItems).zipWhen { items ->
                Flux.fromIterable(items.asIterable()).flatMap {
                    val itemEntity = it
                    val itemState = LIST_ITEM_STATE.values().first { it.value == itemEntity.itemState }
                    val userMetaDataTO = ListMapper.getUserMetaDataFromMetadataMap(itemEntity.itemMetadata)
                    eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(),
                            DeleteListItemNotifyEvent(guestId, itemEntity.id!!,
                                    listOf(MultiDeleteListItem(itemEntity.itemId!!, itemEntity.itemTcin, itemEntity.itemTitle,
                                            itemEntity.itemReqQty, itemState, userMetaDataTO?.userMetaData))), listId.toString())
                }.collectList()
            }.map { it.t1 }
        }
    }
}