package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListItemExtEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.service.transform.TransformationContext
import com.tgt.lists.atlas.api.service.transform.list_items.ListItemsTransformationPipeline
import com.tgt.lists.atlas.api.service.transform.list_items.ListItemsTransformationPipelineConfiguration
import com.tgt.lists.atlas.api.service.transform.list_items.PaginateListItemsTransformationStep.Companion.MAX_PAGE_COUNT
import com.tgt.lists.atlas.api.transport.ListItemResponseTO
import com.tgt.lists.atlas.api.transport.ListResponseTO
import com.tgt.lists.atlas.api.transport.mapper.ListItemMapper
import com.tgt.lists.atlas.api.transport.mapper.ListMapper
import com.tgt.lists.atlas.api.util.Constants.LIST_ITEM_STATE_KEY
import com.tgt.lists.atlas.api.util.ItemIncludeFields
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import io.micronaut.context.annotation.Value
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetListService(
    @Inject private val listRepository: ListRepository,
    @Inject private val listItemsTransformationPipelineConfiguration: ListItemsTransformationPipelineConfiguration
) {
    private val logger = KotlinLogging.logger {}

    @Value("\${list.max-pending-item-count}")
    private var maxPendingItemCount: Int = 100 // default max item count

    @Value("\${list.max-completed-items-count}")
    private var maxCompletedItemsCount: Int = 100 // default max item count

    fun getList(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        locationId: Long,
        listId: UUID,
        listItemsTransformationPipeline: ListItemsTransformationPipeline,
        includeItems: ItemIncludeFields
    ): Mono<ListResponseTO> {

        logger.debug("[getList] guestId: $guestId, listId: $listId, locationId: $locationId")

        return when (includeItems) {
            ItemIncludeFields.PENDING -> {
                // include only pending items
                listRepository.findListAndItemsByListIdAndItemState(listId, LIST_ITEM_STATE.PENDING.value)
                        .collectList()
                        .flatMap {
                            val pendingListItems = it
                            if (pendingListItems.isEmpty()) {
                                getListResponseWithNoItems(listId)
                            } else {
                                val listEntity = ListMapper.toListEntity(pendingListItems.first())
                                val pendingListTransformedPair = transformListItems(guestId, listId, false, pendingListItems, listItemsTransformationPipeline)
                                pendingListTransformedPair.second
                                        .map {
                                            val maxPendingPageCount: Int? = try {
                                                pendingListTransformedPair.first.getContextValue(MAX_PAGE_COUNT) as Int
                                            } catch (e: Exception) {
                                                null
                                            }
                                            toListResponse(listEntity, it, emptyList(), maxPendingPageCount, null)
                                        }
                            }
                        }
            }
            ItemIncludeFields.COMPLETED -> {
                // include only completed items
                listRepository.findListAndItemsByListIdAndItemState(listId, LIST_ITEM_STATE.COMPLETED.value)
                        .collectList()
                        .flatMap {
                            val completedListItems = it
                            if (completedListItems.isEmpty()) {
                                getListResponseWithNoItems(listId)
                            } else {
                                val listEntity = ListMapper.toListEntity(completedListItems.first())
                                val completedListTransformedPair = transformListItems(guestId, listId, true, completedListItems, listItemsTransformationPipeline)
                                completedListTransformedPair.second
                                        .map {
                                            val maxCompletedPageCount: Int? = try {
                                                completedListTransformedPair.first.getContextValue(MAX_PAGE_COUNT) as Int
                                            } catch (e: Exception) {
                                                null
                                            }
                                            toListResponse(listEntity, emptyList(), it, null, maxCompletedPageCount)
                                        }
                            }
                        }
            }
            ItemIncludeFields.ALL -> {
                // include both pending and completed items
                listRepository.findListAndItemsByListId(listId)
                        .collectList()
                        .flatMap {
                            val listItems = it
                            if (listItems.isEmpty()) {
                                getListResponseWithNoItems(listId)
                            } else {
                                val pendingListItems = listItems.filter { it.itemState == LIST_ITEM_STATE.PENDING.value }
                                val completedListItems = listItems.filter { it.itemState == LIST_ITEM_STATE.COMPLETED.value }
                                val listEntity = ListMapper.toListEntity(listItems.first())
                                val pendingListTransformedPair = transformListItems(guestId, listId, false, pendingListItems, listItemsTransformationPipeline)
                                val completedListTransformedPair = transformListItems(guestId, listId, true, completedListItems, listItemsTransformationPipeline)

                                pendingListTransformedPair.second
                                        .zipWith(completedListTransformedPair.second)
                                        .map {
                                            val pendingItems: List<ListItemResponseTO> = it.t1
                                            val completedItems: List<ListItemResponseTO> = it.t2
                                            val maxPendingPageCount: Int? = try {
                                                pendingListTransformedPair.first.getContextValue(MAX_PAGE_COUNT) as Int
                                            } catch (e: Exception) {
                                                null
                                            }
                                            val maxCompletedPageCount: Int? = try {
                                                completedListTransformedPair.first.getContextValue(MAX_PAGE_COUNT) as Int
                                            } catch (e: Exception) {
                                                null
                                            }
                                            toListResponse(listEntity, pendingItems, completedItems, maxPendingPageCount, maxCompletedPageCount)
                                        }
                            }
                        }
            }
        }
    }

    private fun toListResponse(
        listEntity: ListEntity,
        pendingItems: List<ListItemResponseTO>?,
        completedItems: List<ListItemResponseTO>?,
        maxPendingPageCount: Int?,
        maxCompletedPageCount: Int?
    ): ListResponseTO {
        return ListMapper.toListResponseTO(listEntity,
                pendingListItems = pendingItems, completedListItems = completedItems,
                maxPendingItemCount = maxPendingItemCount, maxCompletedItemsCount = maxCompletedItemsCount,
                maxPendingPageCount = maxPendingPageCount, maxCompletedPageCount = maxCompletedPageCount)
    }

    private fun getListResponseWithNoItems(listId: UUID): Mono<ListResponseTO> {
        return listRepository.findListById(listId)
                .map {
                    toListResponse(it, emptyList(), emptyList(), null, null)
                }
    }

    private fun transformListItems(
        guestId: String,
        listId: UUID,
        isCompleted: Boolean,
        listItemsResponse: List<ListItemExtEntity>?,
        listItemsTransformationPipeline: ListItemsTransformationPipeline
    ): Pair<TransformationContext, Mono<List<ListItemResponseTO>>> {

        val transformationContext = TransformationContext(transformationPipelineConfiguration = listItemsTransformationPipelineConfiguration)
        transformationContext.addContextValue(LIST_ITEM_STATE_KEY, if (isCompleted) LIST_ITEM_STATE.COMPLETED else LIST_ITEM_STATE.PENDING)

        val listItemsTransformed = listItemsResponse?.let {
            val listItems: List<ListItemResponseTO>? = it
                    .map {
                        ListItemMapper.toListItemResponseTO(it)
                    }
            listItems?.let {
                listItemsTransformationPipeline.executePipeline(guestId, listId, listItems, transformationContext)
            }
        } ?: Mono.just(emptyList())

        return Pair(transformationContext, listItemsTransformed)
    }
}
