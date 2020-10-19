package com.tgt.lists.atlas.api.service.transform.list

import com.tgt.lists.atlas.api.service.transform.TransformationContext
import com.tgt.lists.atlas.api.transport.ListGetAllResponseTO
import reactor.core.publisher.Mono

/**
 * List-of-lists specific pipeline to transform a list of lists via transformation steps
 */
class ListsTransformationPipeline {
    private val steps = mutableListOf<ListsTransformationStep>()

    /**
     * Add an execution step to pipeline
     */
    fun addStep(listsTransformationStep: ListsTransformationStep): ListsTransformationPipeline {
        steps.add(listsTransformationStep)
        return this
    }

    /**
     * Execute all steps of pipeline
     */
    fun executePipeline(guestId: String, lists: List<ListGetAllResponseTO>, transformationContext: TransformationContext): Mono<List<ListGetAllResponseTO>> {
        var response: Mono<List<ListGetAllResponseTO>>? = null
        steps.forEach {
            val step = it
            response = response?.flatMap { step.execute(guestId, it, transformationContext) } ?: step.execute(guestId, lists, transformationContext)
        }
        return response ?: Mono.just(lists)
    }
}