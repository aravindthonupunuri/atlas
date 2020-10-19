package com.tgt.lists.atlas.api.persistence

import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import java.util.*

data class DataContextContainer(
        val listEntityCtxMap: MutableMap<UUID, ListEntity> = mutableMapOf()
)