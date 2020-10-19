package com.tgt.lists.atlas.api.service.transform.list_items

import com.tgt.lists.atlas.api.service.transform.TransformationPipelineConfiguration
import javax.inject.Singleton

/**
 * Configuration for steps in list items transformation pipeline
 */
@Singleton
data class ListItemsTransformationPipelineConfiguration(
    val sortListItemsTransformationConfiguration: SortListItemsTransformationConfiguration? = null,
    val paginateListItemsTransformationConfiguration: PaginateListItemsTransformationConfiguration? = null
) : TransformationPipelineConfiguration