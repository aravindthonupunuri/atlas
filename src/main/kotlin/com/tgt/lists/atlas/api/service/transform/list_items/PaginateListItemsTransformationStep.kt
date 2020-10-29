package com.tgt.lists.atlas.api.service.transform.list_items

import com.tgt.lists.atlas.api.service.transform.TransformationContext
import com.tgt.lists.atlas.api.transport.ListItemResponseTO
import reactor.core.publisher.Mono
import java.util.*

/**
 * Transformation to paginate a list of items into pages
 */
class PaginateListItemsTransformationStep(
    val page: Int
) : ListItemsTransformationStep {

    companion object {
        val MAX_PAGE_COUNT = "MAX_PAGE_COUNT"
    }

    override fun execute(guestId: String, listId: UUID, items: List<ListItemResponseTO>, transformationContext: TransformationContext): Mono<List<ListItemResponseTO>> {
        if (!items.isEmpty()) {
            val pageSize: Int = (transformationContext.transformationPipelineConfiguration as ListItemsTransformationPipelineConfiguration).paginateListItemsTransformationConfiguration?.pageSize ?: 0
            if (pageSize > 0 && page > 0) {
                var maxPageCount = items.size / pageSize
                if (maxPageCount * pageSize < items.size) maxPageCount++

                transformationContext.addContextValue(MAX_PAGE_COUNT, maxPageCount)
                val startIndex = (page - 1) * pageSize
                var endIndex = (page * pageSize) - 1
                if (endIndex > items.size - 1) endIndex = items.size - 1
                val pageItems = items.slice(startIndex..endIndex)
                return Mono.just(pageItems)
            }
        }

        return Mono.just(items)
    }
}