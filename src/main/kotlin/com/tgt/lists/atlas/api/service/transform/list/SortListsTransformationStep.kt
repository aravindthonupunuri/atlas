package com.tgt.lists.atlas.api.service.transform.list

import com.tgt.lists.atlas.api.service.transform.TransformationContext
import com.tgt.lists.atlas.api.transport.ListGetAllResponseTO
import com.tgt.lists.atlas.api.type.ListSortFieldGroup
import com.tgt.lists.atlas.api.type.ListSortOrderGroup
import reactor.core.publisher.Mono

/**
 * Sort step for list-of-lists
 */
class SortListsTransformationStep(
    private val sortFieldBy: ListSortFieldGroup? = null,
    private val sortOrderBy: ListSortOrderGroup? = null
) : ListsTransformationStep {

    override fun execute(
        guestId: String,
        lists: List<ListGetAllResponseTO>,
        transformationContext: TransformationContext
    ): Mono<List<ListGetAllResponseTO>> {
        return if (lists.isNotEmpty()) {
            if (sortFieldBy == ListSortFieldGroup.LIST_POSITION) {
                val listsTransformationPipelineConfiguration = transformationContext.transformationPipelineConfiguration as ListsTransformationPipelineConfiguration
                val guestPreferenceSortOrderManager = listsTransformationPipelineConfiguration.guestPreferenceSortOrderManager!!
                guestPreferenceSortOrderManager.getGuestPreference(guestId).map {
                    guestPreferenceSortOrderManager.sortListOfLists(it.listSortOrder ?: "", lists)
                }
            } else {
                Mono.just(applySort(sortFieldBy, sortOrderBy, lists))
            }
        } else {
            Mono.just(lists)
        }
    }

    private fun applySort(
        sortFieldBy: ListSortFieldGroup?,
        sortOrderBy: ListSortOrderGroup?,
        listGetAllResponses: List<ListGetAllResponseTO>
    ): List<ListGetAllResponseTO> {
        return if (sortOrderBy == null || sortOrderBy == ListSortOrderGroup.ASCENDING) {
            listGetAllResponses.sortedWith(sortFields(sortFieldBy))
        } else {
            listGetAllResponses.sortedWith(sortFieldsDescending(sortFieldBy))
        }
    }

    private fun sortFields(sortFieldBy: ListSortFieldGroup?): Comparator<ListGetAllResponseTO> {
        return when (sortFieldBy) {
            ListSortFieldGroup.LIST_TITLE -> compareBy { it.listTitle?.toLowerCase() }
            ListSortFieldGroup.LAST_MODIFIED_DATE -> compareBy { it.lastModifiedTs }
            ListSortFieldGroup.ADDED_DATE -> compareBy { it.addedTs }
            else -> compareBy { it.lastModifiedTs }
        }
    }

    private fun sortFieldsDescending(sortFieldBy: ListSortFieldGroup?): Comparator<ListGetAllResponseTO> {
        return when (sortFieldBy) {
            ListSortFieldGroup.LIST_TITLE -> compareByDescending { it.listTitle?.toLowerCase() }
            ListSortFieldGroup.LAST_MODIFIED_DATE -> compareByDescending { it.lastModifiedTs }
            ListSortFieldGroup.ADDED_DATE -> compareByDescending { it.addedTs }
            else -> compareByDescending { it.lastModifiedTs }
        }
    }
}