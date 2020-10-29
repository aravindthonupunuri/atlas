package com.tgt.lists.atlas.api.service.transform.list_items

import com.tgt.lists.atlas.api.service.transform.TransformationContext
import com.tgt.lists.atlas.api.transport.ListItemResponseTO
import com.tgt.lists.atlas.api.util.Constants.LIST_ITEM_STATE_KEY
import com.tgt.lists.atlas.api.util.ItemSortFieldGroup
import com.tgt.lists.atlas.api.util.ItemSortOrderGroup
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
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
                        it.itemSortOrderManager?.getList(guestId, listId)
                                ?.map {
                                    reArrangeItems(it.itemSortOrder!!.split(","), items)
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

    private fun reArrangeItems(sortOrderList: List<String>, pendingItems: List<ListItemResponseTO>): List<ListItemResponseTO> {
        var newItemsList: MutableList<ListItemResponseTO>? = arrayListOf()
        // If sortOrderList is null or empty return pending list
        if (sortOrderList.isNotEmpty()) {
            for (itemId in sortOrderList) {
                // when the item is empty return pending Items Ex:List(listId, ",,,")
                if (itemId.isNotEmpty()) {
                    val sortedItem = pendingItems.firstOrNull { pItem ->
                        pItem.listItemId.toString() == itemId && pItem.itemState == LIST_ITEM_STATE.PENDING
                    }
                    sortedItem?.let { newItemsList?.add(sortedItem) }
                }
            }
            // Pending Items check is been done in the caller
            if (newItemsList != null)
                newItemsList.addAll(pendingItems.asIterable().minus(newItemsList))
            else
                newItemsList = pendingItems.toMutableList()
        } else {
            newItemsList = pendingItems.toMutableList()
        }
        return newItemsList
    }
}