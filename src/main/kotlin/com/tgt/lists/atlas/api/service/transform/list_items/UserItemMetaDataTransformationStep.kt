package com.tgt.lists.atlas.api.service.transform.list_items

import com.tgt.lists.atlas.api.transport.UserItemMetaDataTO
import reactor.core.publisher.Mono

interface UserItemMetaDataTransformationStep {

    fun execute(userItemMetaDataTO: UserItemMetaDataTO): Mono<UserItemMetaDataTO>
}