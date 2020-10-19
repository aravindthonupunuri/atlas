package com.tgt.lists.atlas.api.persistence

import com.tgt.lists.atlas.api.domain.model.List
import io.micronaut.data.annotation.Id
import reactor.core.publisher.Mono
import java.util.*

interface ListRepository {
    fun save(list: List): Mono<List>
    fun find(@Id listId: UUID): Mono<List>
    fun updateByListId(@Id listId: UUID, listItemSortOrder: String): Mono<Int>
    fun delete(@Id listId: UUID): Mono<Int>
}
