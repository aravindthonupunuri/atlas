package com.tgt.lists.atlas.api.domain

import com.tgt.lists.atlas.api.transport.ContextContainer
import com.tgt.lists.atlas.api.util.Constants
import reactor.util.context.ContextView
import javax.inject.Singleton

@Singleton
open class ContextContainerManager {
    fun setPartialContentFlag(context: ContextView) {
        if (!context.isEmpty) {
            context.get<ContextContainer>(Constants.CONTEXT_OBJECT).partialResponse = true
        }
    }
}
