package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.domain.EventPublisher
import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.ListDeleteResponseTO
import com.tgt.lists.atlas.api.transport.mapper.ListMapper
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
    @Inject private val eventPublisher: EventPublisher,
    @Inject private val listSortOrderService: ListSortOrderService
) {
    private val logger = KotlinLogging.logger {}

    fun deleteList(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        listId: UUID
    ): Mono<ListDeleteResponseTO> {
        logger.debug("[deleteList] guestId: $guestId, listId: $listId")
        return listRepository.findListById(listId)
                .flatMap { doDelete(guestId, it) }
                .map { ListDeleteResponseTO(it.id) }
                .switchIfEmpty {
                    logger.debug("[deleteList] guestId: $guestId, listId: $listId, List not found")
                    Mono.just(ListDeleteResponseTO(listId))
                }
    }

    private fun doDelete(guestId: String, listEntity: ListEntity): Mono<ListEntity> {
        return listSortOrderService.deleteListSortOrder(guestId, listEntity.id!!)
                .flatMap { listRepository.deleteList(listEntity) }
                .zipWhen {
                    val userMetaDataTO = ListMapper.getUserMetaDataFromMetadataMap(listEntity.metadata)
                    eventPublisher.publishEvent(DeleteListNotifyEvent.getEventType(),
                            DeleteListNotifyEvent(listEntity.guestId!!, listEntity.id!!, listEntity.type!!,
                                    listEntity.title!!, userMetaDataTO?.userMetaData), listEntity.guestId!!)
                }
                .map { it.t1 }
    }
}
