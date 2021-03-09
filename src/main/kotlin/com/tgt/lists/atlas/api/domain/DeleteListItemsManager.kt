package com.tgt.lists.atlas.api.domain

import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.type.ItemType
import com.tgt.lists.atlas.api.type.LIST_ITEM_STATE
import com.tgt.lists.atlas.api.type.UserMetaData.Companion.toUserMetaData
import com.tgt.lists.atlas.api.util.getLocalDateTime
import com.tgt.lists.atlas.api.util.getLocalInstant
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

            listRepository.deleteListItems(listItems)
                    .zipWhen { items ->
                        Flux.fromIterable(items.asIterable())
                                .flatMap {
                                    val itemEntity = it
                                    val itemState = LIST_ITEM_STATE.values().first { it.value == itemEntity.itemState }
                                    val userMetaDataTO = toUserMetaData(itemEntity.itemMetadata)

                                    eventPublisher.publishEvent(DeleteListItemNotifyEvent.getEventType(),
                                            DeleteListItemNotifyEvent(
                                                    listId = itemEntity.id!!,
                                                    deleteListItems = listOf(MultiDeleteListItem(
                                                            itemId = itemEntity.itemId!!,
                                                            tcin = itemEntity.itemTcin,
                                                            itemTitle = itemEntity.itemTitle,
                                                            itemRequestedQuantity = itemEntity.itemReqQty,
                                                            itemState = itemState,
                                                            itemType = ItemType.values().first { itemType -> itemType.value == itemEntity.itemType!! },
                                                            itemRefId = itemEntity.itemRefId,
                                                            dpci = itemEntity.itemDpci,
                                                            barCode = itemEntity.itemBarcode,
                                                            itemDesc = itemEntity.itemDesc,
                                                            channel = itemEntity.itemChannel,
                                                            subChannel = itemEntity.itemSubchannel,
                                                            itemUomQuantity = itemEntity.itemQtyUom,
                                                            itemNotes = itemEntity.itemNotes,
                                                            itemFulfilledQuantity = itemEntity.itemQty,
                                                            itemAgentId = itemEntity.itemAgentId,
                                                            userItemMetaDataTO = userMetaDataTO?.metadata,
                                                            addedDate = getLocalDateTime(itemEntity.itemCreatedAt),
                                                            lastModifiedDate = getLocalDateTime(getLocalInstant())
                                                    )),
                                                    performedBy = guestId
                                            ), listId.toString())
                                }.collectList()
                    }
                    .map { it.t1 }
        }
    }
}
