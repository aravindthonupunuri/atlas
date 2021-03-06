package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.domain.Configuration
import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.service.transform.TransformationContext
import com.tgt.lists.atlas.api.service.transform.list.ListsTransformationPipeline
import com.tgt.lists.atlas.api.service.transform.list.ListsTransformationPipelineConfiguration
import com.tgt.lists.atlas.api.transport.ListGetAllResponseTO
import com.tgt.lists.atlas.api.transport.mapper.ListMapper.Companion.toListGetAllResponseTO
import mu.KotlinLogging
import reactor.core.publisher.Mono
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetAllListService(
    @Inject private val listRepository: ListRepository,
    @Inject private val listsTransformationPipelineConfiguration: ListsTransformationPipelineConfiguration,
    @Inject private val configuration: Configuration
) {
    private val logger = KotlinLogging.logger {}

    private val listType: String = configuration.listType
    private val maxListsCount: Int = configuration.maxListsCount

    fun getAllListsForUser(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        listsTransformationPipeline: ListsTransformationPipeline?
    ): Mono<List<ListGetAllResponseTO>> {

        logger.debug("[getAllListsForUser] guestId: $guestId")

        return listRepository.findGuestLists(guestId, listType)
                .flatMap {
                    if (it.isNullOrEmpty()) {
                        logger.debug("[getAllListsForUser] No lists found for guest with guestId: $guestId and listType: $listType")
                        Mono.just(arrayListOf())
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
        val listOfLists = lists.map { toListGetAllResponseTO(it, maxListsCount) }

        return listsTransformationPipeline?.let {
            val transformationContext = TransformationContext(
                    transformationPipelineConfiguration = listsTransformationPipelineConfiguration)
            it.executePipeline(guestId = guestId, lists = listOfLists, transformationContext = transformationContext)
            } ?: Mono.just(listOfLists)
    }
}
