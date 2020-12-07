package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.domain.DeleteListItemsManager
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.transport.ListItemsDeleteResponseTO
import com.tgt.lists.atlas.api.type.ItemIncludeFields
import com.tgt.lists.atlas.api.type.LIST_ITEM_STATE
import com.tgt.lists.atlas.api.util.ErrorCodes.DELETE_LIST_ITEMS_INCLUDED_FIELD_VIOLATION_ERROR_CODE
import com.tgt.lists.atlas.api.util.ErrorCodes.DELETE_LIST_ITEMS_VIOLATION_ERROR_CODE
import com.tgt.lists.common.components.exception.BadRequestException
import com.tgt.lists.common.components.exception.ErrorCode
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeleteListItemsService(
    @Inject private val deleteListItemsManager: DeleteListItemsManager,
    @Inject private val listRepository: ListRepository
) {
    private val logger = KotlinLogging.logger { DeleteListItemsService::class.java.name }

    /**
     * This method implements the functionality for deleting multiple items based on the itemIds or the
     * IncludedFields(ALL,PENDING,COMPLETED). If itemIds are passed as part of query param we delete those specific
     * items, else we look for IncludedFields and delete the items based on the included fields.
     */
    fun deleteListItems(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        listId: UUID,
        itemIdList: List<UUID>? = null,
        itemIncludeFields: ItemIncludeFields? = null
    ): Mono<ListItemsDeleteResponseTO> {
        logger.debug("[deleteMultipleListItem] guestId: $guestId, listId: $listId")
        if (!itemIdList.isNullOrEmpty() && itemIncludeFields != null) {
            throw BadRequestException(ErrorCode(DELETE_LIST_ITEMS_VIOLATION_ERROR_CODE.first, DELETE_LIST_ITEMS_VIOLATION_ERROR_CODE.second))
        } else {
            return if (itemIdList.isNullOrEmpty()) {
                // delete items according to ItemIncludeFields
                when (itemIncludeFields) {
                    ItemIncludeFields.ALL -> deleteItemsByState(guestId, listId)
                    ItemIncludeFields.COMPLETED -> deleteItemsByState(guestId, listId, LIST_ITEM_STATE.COMPLETED)
                    ItemIncludeFields.PENDING -> deleteItemsByState(guestId, listId, LIST_ITEM_STATE.PENDING)
                    else -> throw BadRequestException(ErrorCode(DELETE_LIST_ITEMS_INCLUDED_FIELD_VIOLATION_ERROR_CODE.first, DELETE_LIST_ITEMS_INCLUDED_FIELD_VIOLATION_ERROR_CODE.second))
                }
            } else {
                deleteItemsByItemIds(guestId, listId, itemIdList)
            }
        }
    }

    /**
     * Delete items based on the passed IncludedFields(ALL,PENDING,COMPLETED).
     */
    private fun deleteItemsByState(
        guestId: String,
        listId: UUID,
        itemState: LIST_ITEM_STATE? = null
    ): Mono<ListItemsDeleteResponseTO> {
        return if (itemState == null) {
            // deleting all items in list
            listRepository.findListItemsByListId(listId)
        } else {
            listRepository.findListItemsByListIdAndItemState(listId, itemState.value)
        }.collectList().flatMap { deleteItems(guestId, listId, it) }
    }

    private fun deleteItemsByItemIds(
        guestId: String,
        listId: UUID,
        itemIdList: List<UUID>
    ): Mono<ListItemsDeleteResponseTO> {
        return listRepository.findListItemsByListId(listId).collectList().flatMap { itemsList ->
            val itemsToDelete = itemsList.filter { itemIdList.contains(it.itemId) }
            deleteItems(guestId, listId, itemsToDelete)
        }
    }

    private fun deleteItems(
        guestId: String,
        listId: UUID,
        itemsToDelete: List<ListItemEntity>
    ): Mono<ListItemsDeleteResponseTO> {
        return deleteListItemsManager.deleteListItems(guestId, listId, itemsToDelete)
                .map { itemsDeleted ->
                    ListItemsDeleteResponseTO(listId, itemsDeleted.map { it.itemId !! })
                }
    }
}
