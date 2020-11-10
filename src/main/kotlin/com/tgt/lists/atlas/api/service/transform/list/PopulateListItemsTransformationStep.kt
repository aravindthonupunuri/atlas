package com.tgt.lists.atlas.api.service.transform.list

import com.tgt.lists.atlas.api.service.transform.TransformationContext
import com.tgt.lists.atlas.api.transport.ListGetAllResponseTO
import com.tgt.lists.atlas.api.transport.ListItemResponseTO
import com.tgt.lists.atlas.api.transport.mapper.ListItemMapper.Companion.toListItemResponseTO
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import mu.KotlinLogging
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux

/**
 * Populate list-items for lists within list-of-lists
 * Populate pendingItemsCount, completedItemsCount and totalItemsCount for each list
 */
class PopulateListItemsTransformationStep : ListsTransformationStep {

    private val logger = KotlinLogging.logger {}

    override fun execute(
        guestId: String,
        lists: List<ListGetAllResponseTO>,
        transformationContext: TransformationContext
    ): Mono<List<ListGetAllResponseTO>> {
        if (lists.isNotEmpty()) {
            return lists.toFlux().flatMap {
                val list: ListGetAllResponseTO = it
                val listsTransformationPipelineConfiguration = transformationContext.transformationPipelineConfiguration as ListsTransformationPipelineConfiguration
                val listRepository = listsTransformationPipelineConfiguration.listRepository
                listRepository.findListItemsByListId(list.listId!!).collectList()
                        .map {
                            val pendingListItems: List<ListItemResponseTO>? = it.filter { it.itemState == LIST_ITEM_STATE.PENDING.value }
                                    .map {
                                        toListItemResponseTO(it)
                                    }.ifEmpty { null }
                            val completedListItems: List<ListItemResponseTO>? = it.filter { it.itemState == LIST_ITEM_STATE.COMPLETED.value }
                                    .map {
                                        toListItemResponseTO(it)
                                    }.ifEmpty { null }
                            val pendingItemsCount = pendingListItems?.size ?: 0
                            val completedItemsCount = completedListItems?.size ?: 0

                            list.copy(pendingItems = pendingListItems, completedItems = completedListItems,
                                    pendingItemsCount = pendingItemsCount, completedItemsCount = completedItemsCount, totalItemsCount = pendingItemsCount + completedItemsCount)
                        }
            }.collectList()
        } else {
            return Mono.just(lists)
        }
    }
}