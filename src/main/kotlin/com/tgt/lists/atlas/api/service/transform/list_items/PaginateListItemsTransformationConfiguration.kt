package com.tgt.lists.atlas.api.service.transform.list_items

import com.tgt.lists.atlas.api.domain.Configuration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Configuration for PaginateListItemsTransformationStep
 */
@Singleton
data class PaginateListItemsTransformationConfiguration(
    @Inject val configuration: Configuration
)