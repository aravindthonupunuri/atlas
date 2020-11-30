package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.domain.Configuration
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.service.transform.list_items.ListItemsTransformationPipeline
import com.tgt.lists.atlas.api.transport.ListResponseTO
import com.tgt.lists.atlas.api.type.ItemIncludeFields
import com.tgt.lists.atlas.api.type.LIST_MARKER
import mu.KotlinLogging
import reactor.core.publisher.Mono
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetDefaultListService(
    @Inject private val listRepository: ListRepository,
    @Inject private val getListService: GetListService,
    @Inject private val configuration: Configuration
) {
    private val logger = KotlinLogging.logger {}

    private val listType: String = configuration.listType

    fun getDefaultList(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        locationId: Long,
        listItemsTransformationPipeline: ListItemsTransformationPipeline,
        includeItems: ItemIncludeFields,
        listSubType: String
    ): Mono<ListResponseTO> {

        logger.debug("[getDefaultList] guestId: $guestId, locationId: $locationId")

        return listRepository.findGuestListByMarker(guestId, listType, listSubType, LIST_MARKER.DEFAULT.value).flatMap {
            getListService.getList(guestId, locationId, it.id!!, listItemsTransformationPipeline, includeItems)
        }
    }
}
