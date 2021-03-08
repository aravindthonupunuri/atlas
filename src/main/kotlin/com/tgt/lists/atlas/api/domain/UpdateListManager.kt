package com.tgt.lists.atlas.api.domain

import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.type.LIST_STATE
import com.tgt.lists.atlas.api.type.UserMetaData.Companion.toUserMetaData
import com.tgt.lists.atlas.api.util.getExpirationDate
import com.tgt.lists.atlas.api.util.getLocalDateTime
import com.tgt.lists.atlas.kafka.model.UpdateListNotifyEvent
import com.tgt.lists.common.components.util.TestListEvaluator
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateListManager(
    @Inject private val listRepository: ListRepository,
    @Inject private val eventPublisher: EventPublisher,
    @Inject private val configuration: Configuration
) {
    private val logger = KotlinLogging.logger { UpdateListManager::class.java.name }
    private val testModeExpiration: Long = configuration.testModeExpiration

    fun updateList(
        guestId: String,
        listId: UUID,
        updatedListEntity: ListEntity,
        existingListEntity: ListEntity
    ): Mono<ListEntity> {
        logger.debug("[updateListItem] Updating list")
        return listRepository.updateList(existingListEntity, updatedListEntity)
                .zipWhen {
                    val userMetaDataTO = toUserMetaData(it.metadata)
                    eventPublisher.publishEvent(
                            UpdateListNotifyEvent.getEventType(),
                            UpdateListNotifyEvent(
                                    guestId = it.guestId!!,
                                    listId = it.id!!,
                                    listType = it.type!!,
                                    listSubType = it.subtype,
                                    channel = it.channel,
                                    subChannel = it.subchannel,
                                    listTitle = it.title,
                                    listState = if (it.state != null)
                                        LIST_STATE.values().first { listState -> listState.value == it.state!! }
                                    else LIST_STATE.INACTIVE,
                                    expiration = if (TestListEvaluator.evaluate()) {
                                        getExpirationDate(LocalDate.now(), testModeExpiration) // expiration should always be 24 hrs for test lists
                                    } else {
                                        it.expiration!!
                                    },
                                    userMetaData = userMetaDataTO?.metadata,
                                    shortDescription = it.description,
                                    defaultList = !it.marker.isNullOrEmpty(),
                                    addedDate = getLocalDateTime(it.createdAt),
                                    lastModifiedDate = getLocalDateTime(it.updatedAt),
                                    performedBy = it.guestId
                            ),
                            guestId)
                }.map { it.t1 }
    }
}
