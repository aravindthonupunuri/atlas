package com.tgt.lists.atlas.api.service.transform.list_items

import io.micronaut.context.annotation.Value
import javax.inject.Singleton

/**
 * Configuration for PaginateListItemsTransformationStep
 */
@Singleton
data class PaginateListItemsTransformationConfiguration(
    @Value("\${list.page-size}") val pageSize: Int? = 0
)