package com.tgt.lists.atlas.api.service.transform.list_items

import com.tgt.lists.atlas.api.service.transform.TransformationContext
import com.tgt.lists.atlas.api.transport.ListItemResponseTO
import com.tgt.lists.atlas.api.util.Constants.LIST_ITEM_STATE_KEY
import com.tgt.lists.atlas.api.type.ItemSortFieldGroup
import com.tgt.lists.atlas.api.type.ItemSortOrderGroup
import com.tgt.lists.atlas.api.type.LIST_ITEM_STATE
import reactor.core.publisher.Mono
import java.util.*

/**
 * Sort List Items based on ItemSortFieldGroup and ItemSortOrderGroup
 */
class SortListItemsTransformationStep(
    val sortFieldBy: ItemSortFieldGroup? = null,
    val sortOrderBy: ItemSortOrderGroup? = null
) : ListItemsTransformationStep {

    override fun execute(guestId: String, listId: UUID, items: List<ListItemResponseTO>, transformationContext: TransformationContext): Mono<List<ListItemResponseTO>> {

        if (!items.isEmpty()) {
            return if (sortFieldBy == ItemSortFieldGroup.ITEM_POSITION) {
                return if (transformationContext.getContextValue(LIST_ITEM_STATE_KEY) == LIST_ITEM_STATE.PENDING) {

                    (transformationContext.transformationPipelineConfiguration as ListItemsTransformationPipelineConfiguration).sortListItemsTransformationConfiguration?.let {
                        val listPreferenceSortOrderManager = it.listPreferenceSortOrderManager
                        listPreferenceSortOrderManager?.getListPreference(guestId, listId)
                                ?.map {
                                    listPreferenceSortOrderManager.sortListItemsByPosition(it.itemSortOrder!!, items)
                                }
                    } ?: Mono.just(items)
                } else {
                    Mono.just(items)
                }
            } else {
                Mono.just(items.sortedWith(itemsSort(sortOrderBy, sortFieldBy)))
            }
        } else {
            return Mono.just(items)
        }
    }

    private fun itemsSort(sortOrderBy: ItemSortOrderGroup?, sortFieldBy: ItemSortFieldGroup?): Comparator<ListItemResponseTO> {
        return if (sortOrderBy == ItemSortOrderGroup.ASCENDING)
            compareBy(sortFields(sortFieldBy))
        else
            compareByDescending(sortFields(sortFieldBy))
    }

    private fun sortFields(sortFieldBy: ItemSortFieldGroup?): (ListItemResponseTO) -> Comparable<*>? {
        return {
            when (sortFieldBy) {
                ItemSortFieldGroup.ITEM_TITLE -> it.itemTitle
                ItemSortFieldGroup.LAST_MODIFIED_DATE -> it.lastModifiedTs
                else -> it.addedTs
            }
        }
    }
}