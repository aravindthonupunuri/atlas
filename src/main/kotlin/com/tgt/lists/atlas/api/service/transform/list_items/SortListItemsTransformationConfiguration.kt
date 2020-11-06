package com.tgt.lists.atlas.api.service.transform.list_items

import com.tgt.lists.atlas.api.domain.ListPreferenceSortOrderManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Configuration for SortListItemsTransformationStep
 */
@Singleton
data class SortListItemsTransformationConfiguration(
    @Inject val listPreferenceSortOrderManager: ListPreferenceSortOrderManager? = null
)