package com.tgt.lists.atlas.api.service.transform.list

import com.tgt.lists.atlas.api.domain.CartManager
import com.tgt.lists.atlas.api.domain.ContextContainerManager
import com.tgt.lists.atlas.api.domain.GuestPreferenceSortOrderManager
import com.tgt.lists.atlas.api.service.transform.TransformationPipelineConfiguration
import com.tgt.lists.atlas.api.util.CartManagerName
import io.micronaut.context.annotation.Value
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Configuration for steps in list-of-lists transformation pipeline
 */
@Singleton
data class ListsTransformationPipelineConfiguration(
    @CartManagerName("ListsTransformationPipelineConfiguration") @Inject val cartManager: CartManager,
    @Inject val contextContainerManager: ContextContainerManager,
    @Inject val guestPreferenceSortOrderManager: GuestPreferenceSortOrderManager? = null,
    @Value("\${list.features.sort-position}") val isPositionSortEnabled: Boolean
) : TransformationPipelineConfiguration
