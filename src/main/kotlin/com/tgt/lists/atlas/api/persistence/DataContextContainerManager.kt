package com.tgt.lists.atlas.api.persistence

import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import mu.KotlinLogging
import reactor.util.context.ContextView
import java.util.*
import javax.inject.Singleton

@Singleton
class DataContextContainerManager {
    private val logger = KotlinLogging.logger {}

    companion object {
        const val DATA_CONTEXT_OBJECT = "DATA_CONTEXT_OBJECT"
    }

    fun getListEntity(context: ContextView, id: UUID): ListEntity? {
        getDataContextContainer(context)?.let {
            return it.listEntityCtxMap.get(id)
        }
        return null
    }

    fun setListEntity(context: ContextView, id: UUID, listEntity: ListEntity) {
        getDataContextContainer(context)?.let {
            it.listEntityCtxMap.putIfAbsent(id, listEntity)
        }
    }

    private fun getDataContextContainer(context: ContextView): DataContextContainer? {
        return if (!context.isEmpty && context.hasKey(DATA_CONTEXT_OBJECT)) {
            context.get<DataContextContainer>(DATA_CONTEXT_OBJECT)
        } else {
            logger.debug("No subscriberContext caching enabled for $DATA_CONTEXT_OBJECT")
            null
        }
    }
}