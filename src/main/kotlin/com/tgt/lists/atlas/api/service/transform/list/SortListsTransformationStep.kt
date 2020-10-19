package com.tgt.lists.atlas.api.service.transform.list

import com.tgt.lists.atlas.api.service.transform.TransformationContext
import com.tgt.lists.atlas.api.transport.ListGetAllResponseTO
import com.tgt.lists.atlas.api.util.ListSortFieldGroup
import com.tgt.lists.atlas.api.util.ListSortOrderGroup
import reactor.core.publisher.Mono

/**
 * Sort step for list-of-lists
 */
class SortListsTransformationStep(
    val sortFieldBy: ListSortFieldGroup? = null,
    val sortOrderBy: ListSortOrderGroup? = null
) : ListsTransformationStep {
    override fun execute(guestId: String, lists: List<ListGetAllResponseTO>, transformationContext: TransformationContext): Mono<List<ListGetAllResponseTO>> {
        if (!lists.isEmpty()) {
            if (sortFieldBy == ListSortFieldGroup.LIST_POSITION) {
                return getListSortOrder(guestId, transformationContext).map {
                    val listSortOrderMap = it
                    val wrappedList = lists.map {
                        var position = listSortOrderMap[it.listId.toString()]
                        if (position == null && (sortOrderBy == null ||
                                        sortOrderBy == ListSortOrderGroup.ASCENDING)) {
                            position = Int.MAX_VALUE
                        } else if (position == null) {
                            position = Int.MIN_VALUE
                        }
                        ListGetAllResponseTOWrapper(position, it)
                    }

                    wrappedList.sortedWith(sortPositionFields()).map { it.listGetAllResponseTO }
                }
            } else {
                return Mono.just(applySort(sortFieldBy, sortOrderBy, lists))
            }
        } else {
            return Mono.just(lists)
        }
    }

    private fun getListSortOrder(guestId: String, transformationContext: TransformationContext): Mono<Map<String, Int>> {

        val listsTransformationPipelineConfiguration = transformationContext.transformationPipelineConfiguration as ListsTransformationPipelineConfiguration

        return if (!listsTransformationPipelineConfiguration.isPositionSortEnabled || sortFieldBy == null || sortFieldBy != ListSortFieldGroup.LIST_POSITION) {
            Mono.just(mapOf())
        } else {
            listsTransformationPipelineConfiguration.guestPreferenceSortOrderManager!!.getGuestPreference(guestId)
                    .map {
                        it.listSortOrder.split(",").mapIndexed {
                            index, s -> s to index
                        }.toMap()
                    }
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
            else -> compareBy { it.lastModifiedTs }
        }
    }

    private fun sortFieldsDescending(sortFieldBy: ListSortFieldGroup?): Comparator<ListGetAllResponseTO> {
        return when (sortFieldBy) {
            ListSortFieldGroup.LIST_TITLE -> compareByDescending { it.listTitle?.toLowerCase() }
            ListSortFieldGroup.LAST_MODIFIED_DATE -> compareByDescending { it.lastModifiedTs }
            else -> compareByDescending { it.lastModifiedTs }
        }
    }

    private fun sortPositionFields(): Comparator<ListGetAllResponseTOWrapper> {
        if (sortOrderBy == null || sortOrderBy == ListSortOrderGroup.ASCENDING)
            return compareBy { it.listPosition }
        else
            return compareByDescending { it.listPosition }
    }

    /**
     * Used for add listPosition field to assist in sortinh via standard comparator
     */
    data class ListGetAllResponseTOWrapper(
        val listPosition: Int,
        val listGetAllResponseTO: ListGetAllResponseTO
    )
}