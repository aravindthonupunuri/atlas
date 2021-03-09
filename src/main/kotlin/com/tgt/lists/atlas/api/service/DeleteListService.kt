package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.domain.EventPublisher
import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.ListDeleteResponseTO
import com.tgt.lists.atlas.api.type.LIST_STATE
import com.tgt.lists.atlas.api.type.UserMetaData.Companion.toUserMetaData
import com.tgt.lists.atlas.api.util.getLocalDateTime
import com.tgt.lists.atlas.api.util.getLocalInstant
import com.tgt.lists.atlas.kafka.model.DeleteListNotifyEvent
import mu.KotlinLogging
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeleteListService(
    @Inject private val listRepository: ListRepository,
    @Inject private val eventPublisher: EventPublisher
) {
    private val logger = KotlinLogging.logger {}

    fun deleteList(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        listId: UUID
    ): Mono<ListDeleteResponseTO> {
        logger.debug("[deleteList] guestId: $guestId, listId: $listId")
        return listRepository.findListById(listId)
                .flatMap { doDelete(it, guestId) }
                .map { ListDeleteResponseTO(it.id) }
                .switchIfEmpty {
                    logger.debug("[deleteList] guestId: $guestId, listId: $listId, List not found")
                    Mono.just(ListDeleteResponseTO(listId))
                }
    }

    private fun doDelete(listEntity: ListEntity, guestId: String): Mono<ListEntity> {
        return listRepository.deleteList(listEntity)
                .zipWhen {
                    val userMetaDataTO = toUserMetaData(listEntity.metadata)
                    eventPublisher.publishEvent(DeleteListNotifyEvent.getEventType(), DeleteListNotifyEvent(
                            guestId = listEntity.guestId!!,
                            listId = listEntity.id!!,
                            listType = listEntity.type!!,
                            listSubType = listEntity.subtype,
                            listTitle = listEntity.title!!,
                            channel = listEntity.channel,
                            subChannel = listEntity.subchannel,
                            location = listEntity.location,
                            marker = listEntity.marker,
                            agentId = listEntity.agentId,
                            listState = if (listEntity.state != null)
                                LIST_STATE.values().first { listState -> listState.value == listEntity.state!! }
                            else LIST_STATE.INACTIVE,
                            expiration = listEntity.expiration,
                            shortDescription = listEntity.description,
                            defaultList = !listEntity.marker.isNullOrEmpty(),
                            userMetaData = userMetaDataTO?.metadata,
                            performedBy = guestId,
                            addedDate = getLocalDateTime(listEntity.createdAt),
                            lastModifiedDate = getLocalDateTime(getLocalInstant())
                    ), listEntity.guestId!!)
                }
                .map { it.t1 }
    }
}
