package com.tgt.lists.atlas.api.service.transform.list

import com.tgt.lists.atlas.api.service.transform.TransformationContext
import com.tgt.lists.atlas.api.transport.ListGetAllResponseTO
import reactor.core.publisher.Mono

interface ListsTransformationStep {
    /**
     * @param guestId - guest id
     * @param lists - list of lists
     * @param transformationContext - A data container to pass additional data across pipeline. It can be used by individual
     *                                pipeline steps to read or write additional step specific data that will available at the
     *                                end of pipeline processing for pipeline caller's consumption.
     */
    fun execute(guestId: String, lists: List<ListGetAllResponseTO>, transformationContext: TransformationContext): Mono<List<ListGetAllResponseTO>>
}