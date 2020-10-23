package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.domain.DefaultListManager
import com.tgt.lists.atlas.api.domain.EventPublisher
import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.ListResponseTO
import com.tgt.lists.atlas.api.transport.ListUpdateRequestTO
import com.tgt.lists.atlas.api.transport.mapper.ListMapper.Companion.getUserMetaDataFromMetadataMap
import com.tgt.lists.atlas.api.transport.mapper.ListMapper.Companion.toListResponseTO
import com.tgt.lists.atlas.api.transport.mapper.ListMapper.Companion.toUpdateListEntity
import com.tgt.lists.atlas.kafka.model.UpdateListNotifyEvent
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateListService(
    @Inject private val listRepository: ListRepository,
    @Inject private val defaultListManager: DefaultListManager,
    @Inject private val eventPublisher: EventPublisher
) {
    private val logger = KotlinLogging.logger {}

    fun updateList(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        listId: UUID,
        listUpdateRequestTO: ListUpdateRequestTO
    ): Mono<ListResponseTO> {

        logger.debug("[updateList] guestId: $guestId, listId: $listId")

        return defaultListManager.processDefaultListInd(guestId, listUpdateRequestTO.validate().defaultList ?: false, listId)
            .flatMap { processListUpdate(listId, listUpdateRequestTO) }
    }

    private fun processListUpdate(listId: UUID, listUpdateRequestTO: ListUpdateRequestTO): Mono<ListResponseTO> {
        return listRepository.findListById(listId)
                .flatMap {
                    val existingListEntity = it
                    updateListEntity(existingListEntity, listUpdateRequestTO)
                }
                .flatMap {
                    val existingListEntity = it.first
                    val updatedListEntity = it.second
                    persistUpdatedListEntity(existingListEntity, updatedListEntity)
                }
                .map { toListResponseTO(it) }
    }

    private fun updateListEntity(existingListEntity: ListEntity, listUpdateRequestTO: ListUpdateRequestTO): Mono<Pair<ListEntity, ListEntity>> {
        val existingUserMetadata = getUserMetaDataFromMetadataMap(existingListEntity.metadata)

        existingUserMetadata?.let {
            listUpdateRequestTO.userMetaDataTransformationStep?.let {
                return it.execute(existingUserMetadata)
                        .map {
                            val updatedMetaData = it
                            toUpdateListEntity(existingListEntity, updatedMetaData, listUpdateRequestTO)
                        }
            }
        }
        return Mono.just(toUpdateListEntity(existingListEntity, existingUserMetadata, listUpdateRequestTO))
    }

    private fun persistUpdatedListEntity(existingListEntity: ListEntity, updatedListEntity: ListEntity): Mono<ListEntity> {
        return listRepository.updateList(existingListEntity, updatedListEntity)
                .zipWhen {
                    val userMetaDataTO = getUserMetaDataFromMetadataMap(it.metadata)
                    eventPublisher.publishEvent(UpdateListNotifyEvent.getEventType(),
                            UpdateListNotifyEvent(it.guestId!!, it.id!!, it.type!!, it.title, userMetaDataTO?.userMetaData), it.guestId!!)
                }
                .map { it.t1 }
    }
}
