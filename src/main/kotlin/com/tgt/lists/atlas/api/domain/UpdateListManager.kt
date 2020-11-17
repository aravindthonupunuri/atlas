package com.tgt.lists.atlas.api.domain

import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.mapper.ListMapper
import com.tgt.lists.atlas.api.util.LIST_STATE
import com.tgt.lists.atlas.kafka.model.UpdateListNotifyEvent
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateListManager(
    @Inject private val listRepository: ListRepository,
    @Inject private val eventPublisher: EventPublisher
) {

    private val logger = KotlinLogging.logger { UpdateListManager::class.java.name }

    fun updateList(
        guestId: String,
        listId: UUID,
        updatedListEntity: ListEntity,
        existingListEntity: ListEntity
    ): Mono<ListEntity> {
        logger.debug("[updateListItem] Updating list")
        return listRepository.updateList(existingListEntity, updatedListEntity)
                .zipWhen {
                    val userMetaDataTO = ListMapper.getUserMetaDataFromMetadataMap(it.metadata)
                    eventPublisher.publishEvent(UpdateListNotifyEvent.getEventType(),
                            UpdateListNotifyEvent(
                                    guestId = it.guestId!!,
                                    listId = it.id!!,
                                    listType = it.type!!,
                                    listTitle = it.title,
                                    listState = if (it.state != null)
                                        LIST_STATE.values().first { listState -> listState.value == it.state!! }
                                    else LIST_STATE.INACTIVE,
                                    userMetaData = userMetaDataTO?.userMetaData
                            ), it.guestId!!)
                }
                .map { it.t1 }
    }
}