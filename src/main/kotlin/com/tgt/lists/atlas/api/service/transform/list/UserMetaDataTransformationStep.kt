package com.tgt.lists.atlas.api.service.transform.list

import com.tgt.lists.atlas.api.transport.UserMetaDataTO
import reactor.core.publisher.Mono

interface UserMetaDataTransformationStep {

    fun execute(userMetaDataTO: UserMetaDataTO): Mono<UserMetaDataTO>
}