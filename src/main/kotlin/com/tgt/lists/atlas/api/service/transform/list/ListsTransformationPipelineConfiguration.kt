package com.tgt.lists.atlas.api.service.transform.list

import com.tgt.lists.atlas.api.domain.ContextContainerManager
import com.tgt.lists.atlas.api.domain.GuestPreferenceSortOrderManager
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.service.transform.TransformationPipelineConfiguration
import io.micronaut.context.annotation.Value
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Configuration for steps in list-of-lists transformation pipeline
 */
@Singleton
data class ListsTransformationPipelineConfiguration(
    @Inject val listRepository: ListRepository,
    @Inject val contextContainerManager: ContextContainerManager,
    @Inject val guestPreferenceSortOrderManager: GuestPreferenceSortOrderManager? = null,
    @Value("\${list.features.sort-position}") val isPositionSortEnabled: Boolean
) : TransformationPipelineConfiguration
