package com.tgt.lists.atlas.api.service.transform.list

import com.tgt.lists.atlas.api.type.UserMetaData
import reactor.core.publisher.Mono

interface UserMetaDataTransformationStep {
    fun execute(userMetaData: UserMetaData): Mono<UserMetaData>
}