package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.domain.DefaultListManager
import com.tgt.lists.atlas.api.domain.UpdateListManager
import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.ListResponseTO
import com.tgt.lists.atlas.api.transport.ListUpdateRequestTO
import com.tgt.lists.atlas.api.transport.mapper.ListMapper.Companion.toListResponseTO
import com.tgt.lists.atlas.api.transport.mapper.ListMapper.Companion.toUpdateListEntity
import com.tgt.lists.atlas.api.type.UserMetaData.Companion.toUserMetaData
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateListService(
    @Inject private val listRepository: ListRepository,
    @Inject private val defaultListManager: DefaultListManager,
    @Inject private val updateListManager: UpdateListManager
) {
    private val logger = KotlinLogging.logger {}

    fun updateList(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        listId: UUID,
        listUpdateRequestTO: ListUpdateRequestTO
    ): Mono<ListResponseTO> {

        logger.debug("[updateList] guestId: $guestId, listId: $listId")

        return defaultListManager.processDefaultListInd(guestId, listUpdateRequestTO.validate().defaultList ?: false, listId)
            .flatMap { processListUpdate(guestId, listId, listUpdateRequestTO) }
    }

    private fun processListUpdate(guestId: String, listId: UUID, listUpdateRequestTO: ListUpdateRequestTO): Mono<ListResponseTO> {
        return listRepository.findListById(listId)
                .flatMap {
                    val existingListEntity = it
                    updateListEntity(existingListEntity, listUpdateRequestTO)
                }
                .flatMap {
                    val existingListEntity = it.first
                    val updatedListEntity = it.second
                    updateListManager.updateList(guestId, listId, updatedListEntity, existingListEntity)
                }
                .map { toListResponseTO(it) }
    }

    private fun updateListEntity(existingListEntity: ListEntity, listUpdateRequestTO: ListUpdateRequestTO): Mono<Pair<ListEntity, ListEntity>> {
        val existingUserMetadata = toUserMetaData(existingListEntity.metadata)

        existingUserMetadata?.let {
            listUpdateRequestTO.userMetaDataTransformationStep.let {
                return it.execute(existingUserMetadata)
                        .map {
                            val updatedMetaData = it
                            toUpdateListEntity(existingListEntity, updatedMetaData, listUpdateRequestTO)
                        }
            }
        }
        return Mono.just(toUpdateListEntity(existingListEntity, existingUserMetadata, listUpdateRequestTO))
    }
}
