package com.tgt.lists.atlas.api.service.transform.list_items

import com.tgt.lists.atlas.api.service.transform.TransformationContext
import com.tgt.lists.atlas.api.transport.ListItemResponseTO
import reactor.core.publisher.Mono
import java.util.*

/**
 * ListItems specific pipeline to transform a list of items via transformation steps
 */
class ListItemsTransformationPipeline {
    private val steps = mutableListOf<ListItemsTransformationStep>()

    /**
     * Add an execution step to pipeline
     */
    fun addStep(listItemsTransformationStep: ListItemsTransformationStep): ListItemsTransformationPipeline {
        steps.add(listItemsTransformationStep)
        return this
    }

    /**
     * Execute all steps of pipeline
     */
    fun executePipeline(listId: UUID, items: List<ListItemResponseTO>, transformationContext: TransformationContext): Mono<List<ListItemResponseTO>> {
        var response: Mono<List<ListItemResponseTO>>? = null
        steps.forEach {
            val step = it
            response = response?.flatMap { step.execute(listId, it, transformationContext) } ?: step.execute(listId, items, transformationContext)
        }
        return response ?: Mono.just(items)
    }
}