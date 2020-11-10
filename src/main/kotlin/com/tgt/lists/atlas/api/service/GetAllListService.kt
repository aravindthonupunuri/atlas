package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.service.transform.TransformationContext
import com.tgt.lists.atlas.api.service.transform.list.ListsTransformationPipeline
import com.tgt.lists.atlas.api.service.transform.list.ListsTransformationPipelineConfiguration
import com.tgt.lists.atlas.api.transport.ListGetAllResponseTO
import com.tgt.lists.atlas.api.transport.mapper.ListMapper.Companion.getUserMetaDataFromMetadataMap
import com.tgt.lists.atlas.api.util.LIST_MARKER
import com.tgt.lists.atlas.api.util.LIST_STATE
import com.tgt.lists.atlas.api.util.getLocalDateTimeFromInstant
import io.micronaut.context.annotation.Value
import mu.KotlinLogging
import reactor.core.publisher.Mono
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetAllListService(
    @Inject private val listRepository: ListRepository,
    @Inject private val listsTransformationPipelineConfiguration: ListsTransformationPipelineConfiguration,
    @Value("\${list.list-type}") private val listType: String,
    @Value("\${list.max-count}") private val maxListsCount: Int
) {
    private val logger = KotlinLogging.logger {}

    fun getAllListsForUser(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        listsTransformationPipeline: ListsTransformationPipeline?
    ): Mono<List<ListGetAllResponseTO>> {

        logger.debug("[getAllListsForUser] guestId: $guestId")

        return listRepository.findGuestLists(guestId, listType).flatMap {
            if (it.isNullOrEmpty()) {
                logger.debug("[getAllListsForUser] No lists found for guest with guestId: $guestId and listType: $listType")
                Mono.empty()
            } else {
                process(guestId, it, listsTransformationPipeline)
            }
        }
    }

    private fun process(
        guestId: String,
        lists: List<ListEntity>,
        listsTransformationPipeline: ListsTransformationPipeline?
    ): Mono<List<ListGetAllResponseTO>> {
        val listOfLists = lists
                    .map {
                        ListGetAllResponseTO(
                                listId = it.id,
                                channel = it.channel,
                                listType = it.type,
                                listSubType = it.subtype,
                                listState = if (it.state != null)
                                    LIST_STATE.values().first { listState -> listState.value == it.state!! }
                                else LIST_STATE.INACTIVE,
                                listTitle = it.title,
                                shortDescription = it.description,
                                agentId = it.agentId,
                                metadata = getUserMetaDataFromMetadataMap(it.metadata)?.userMetaData,
                                defaultList = (it.marker == LIST_MARKER.DEFAULT.value),
                                maxListsCount = maxListsCount,
                                addedTs = getLocalDateTimeFromInstant(it.createdAt),
                                lastModifiedTs = getLocalDateTimeFromInstant(it.updatedAt)
                        )
                    }

            return listsTransformationPipeline?.let {
                val transformationContext = TransformationContext(transformationPipelineConfiguration = listsTransformationPipelineConfiguration)
                it.executePipeline(guestId = guestId, lists = listOfLists, transformationContext = transformationContext)
            } ?: Mono.just(listOfLists)
    }
}
