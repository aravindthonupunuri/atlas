package com.tgt.lists.atlas.api.service

import com.tgt.lists.atlas.api.domain.DeleteCartItemsManager
import com.tgt.lists.atlas.api.transport.ListItemMultiDeleteResponseTO
import com.tgt.lists.atlas.api.util.AppErrorCodes.DELETE_CART_ITEMS_INCLUDED_FIELD_VIOLATION_ERROR_CODE
import com.tgt.lists.atlas.api.util.GuestId
import com.tgt.lists.atlas.api.util.ItemIncludeFields
import com.tgt.lists.cart.transport.DeleteMultiCartItemsResponse
import com.tgt.lists.common.components.exception.BadRequestException
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeleteMultipleListItemService(
    @Inject private val deleteCartItemsManager: DeleteCartItemsManager
) {

    private val logger = KotlinLogging.logger { DeleteMultipleListItemService::class.java.name }

    /**
     * This method implements the functionality for deleting multiple cart items based on the cartItemIds or the
     * IncludedFields(ALL,PENDING,COMPLETED). If itemIds are passed as part of query param we delete those specific cart
     * items, else we look for IncludedFields and delete the items based on the included fields.
     */
    fun deleteMultipleListItem(
        guestId: GuestId, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        listId: UUID,
        itemIdList: List<UUID>?,
        deleteFields: ItemIncludeFields?
    ): Mono<ListItemMultiDeleteResponseTO> {

        logger.debug("[deleteMultipleListItem] guestId: $guestId, listId: $listId")

        return if (itemIdList.isNullOrEmpty()) {
            // If itemsIds are not specified then delete the cart items based on the included field passed
            processDeleteCartItems(guestId, listId, deleteFields)
        } else {
            processDeleteSpecificCartItemIds(guestId, listId, itemIdList)
        }.flatMap { toListItemMultiDeleteResponseTO(it) }
    }

    /**
     * Delete cartItems based on the passed IncludedFields(ALL,PENDING,COMPLETED).
     */
    private fun processDeleteCartItems(
        guestId: GuestId,
        listId: UUID,
        deleteFields: ItemIncludeFields?
    ): Mono<DeleteMultiCartItemsResponse> {
        return when (deleteFields) {
            ItemIncludeFields.ALL -> deleteCartItemsManager.deleteAllCartItems(guestId, listId)
            ItemIncludeFields.COMPLETED -> deleteCartItemsManager.deleteCompletedCartItems(guestId, listId)
            ItemIncludeFields.PENDING -> deleteCartItemsManager.processDeleteCartItems(guestId, listId)
            else -> throw BadRequestException(DELETE_CART_ITEMS_INCLUDED_FIELD_VIOLATION_ERROR_CODE)
        }
    }

    /**
     * Delete cartItems based on the passed cartItemIds from the query param. We first try to delete the items from the
     * PENDING cart, in case of any failed cartItems we try to delete it from the COMPLETED cart.
     */
    private fun processDeleteSpecificCartItemIds(
        guestId: GuestId,
        listId: UUID,
        itemIdList: List<UUID>
    ): Mono<DeleteMultiCartItemsResponse> {
        return deleteCartItemsManager.deleteCartItems(guestId, listId, itemIdList.toSet())
    }

    private fun toListItemMultiDeleteResponseTO(deleteMultiCartItemsResponse: DeleteMultiCartItemsResponse): Mono<ListItemMultiDeleteResponseTO> {
        return Mono.just(ListItemMultiDeleteResponseTO(deleteMultiCartItemsResponse.cartId,
            deleteMultiCartItemsResponse.deletedCartItemIds?.toList(), deleteMultiCartItemsResponse.failedCartItemIds?.toList()))
    }

    private fun getUUID(itemId: String?): UUID? {
        return try { UUID.fromString(itemId) } catch (ex: IllegalArgumentException) {
            logger.error("Cannot convert String itemId to UUID for: $itemId", ex)
            return null
        }
    }
}
