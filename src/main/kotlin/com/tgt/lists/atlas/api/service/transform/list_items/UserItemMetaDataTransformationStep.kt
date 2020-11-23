package com.tgt.lists.atlas.api.service.transform.list_items

import com.tgt.lists.atlas.api.type.UserMetaData
import reactor.core.publisher.Mono

interface UserItemMetaDataTransformationStep {
    fun execute(userItemMetaDataTO: UserMetaData): Mono<UserMetaData>
}