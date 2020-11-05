package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.domain.DefaultListManager
import com.tgt.lists.atlas.api.domain.EventPublisher
import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.ListRequestTO
import com.tgt.lists.atlas.api.transport.ListResponseTO
import com.tgt.lists.atlas.api.transport.mapper.ListMapper.Companion.getUserMetaDataFromMetadataMap
import com.tgt.lists.atlas.api.transport.mapper.ListMapper.Companion.toListResponseTO
import com.tgt.lists.atlas.api.transport.mapper.ListMapper.Companion.toNewListEntity
import com.tgt.lists.atlas.kafka.model.CreateListNotifyEvent
import io.micronaut.context.annotation.Value
import mu.KotlinLogging
import reactor.core.publisher.Mono
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreateListService(
    @Inject private val listRepository: ListRepository,
    @Inject private val defaultListManager: DefaultListManager,
    @Inject private val eventPublisher: EventPublisher,
    @Value("\${list.expiration-days}") private val expirationDays: Long = 0, // default value of 0 days
    @Value("\${list.test-mode:false}") private val testMode: Boolean = false
) {
    private val logger = KotlinLogging.logger {}

    @Value("\${list.list-type}")
    private var listType: String = ""

    fun createList(
        guestId: String, // this is the ownerId of list
        listRequestTO: ListRequestTO
    ): Mono<ListResponseTO> {

        logger.debug("[createList] guestId: $guestId, listRequestTO: $listRequestTO")

        return defaultListManager.processDefaultListInd(guestId, listRequestTO.defaultList)
                .flatMap {
                    val listEntity = toNewListEntity(guestId = guestId,
                            listType = listType,
                            listSubtype = listRequestTO.listSubType,
                            listRequestTO = listRequestTO,
                            defaultList = it,
                            expirationDays = expirationDays,
                            testList = testMode)
                    persistNewList(guestId, listEntity)
                }
                .map { toListResponseTO(it) }
    }

    private fun persistNewList(guestId: String, listEntity: ListEntity): Mono<ListEntity> {

        return listRepository.saveList(listEntity)
                .zipWhen {
                    val userMetaDataTO = getUserMetaDataFromMetadataMap(listEntity.metadata)
                    eventPublisher.publishEvent(CreateListNotifyEvent.getEventType(),
                        CreateListNotifyEvent(guestId, it.id!!, it.type!!, it.title!!, userMetaDataTO?.userMetaData), guestId) }
                .map { it.t1 }
    }
}
