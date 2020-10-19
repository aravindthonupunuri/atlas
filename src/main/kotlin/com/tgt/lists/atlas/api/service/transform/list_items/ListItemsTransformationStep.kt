package com.tgt.lists.atlas.api.service.transform.list_items

import com.tgt.lists.atlas.api.service.transform.TransformationContext
import com.tgt.lists.atlas.api.transport.ListItemResponseTO
import reactor.core.publisher.Mono
import java.util.*

interface ListItemsTransformationStep {
    /**
     * @param listId - list id
     * @param items - list items
     * @param transformationContext - A data container to pass additional data across pipeline. It can be used by individual
     *                                pipeline steps to read or write additional step specific data that will available at the
     *                                end of pipeline processing for pipeline caller's consumption.
     */
    fun execute(listId: UUID, items: List<ListItemResponseTO>, transformationContext: TransformationContext): Mono<List<ListItemResponseTO>>
}