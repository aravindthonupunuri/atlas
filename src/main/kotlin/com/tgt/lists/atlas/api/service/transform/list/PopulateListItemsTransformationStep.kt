package com.tgt.lists.atlas.api.service.transform.list

import com.tgt.lists.cart.transport.CartContentsResponse
import com.tgt.lists.atlas.api.service.transform.TransformationContext
import com.tgt.lists.atlas.api.transport.ListGetAllResponseTO
import com.tgt.lists.atlas.api.transport.ListItemResponseTO
import com.tgt.lists.atlas.api.transport.toListItemResponseTO
import mu.KotlinLogging
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux
import java.util.*

/**
 * Populate list-items for lists within list-of-lists
 * Populate pendingItemsCount, completedItemsCount and totalItemsCount for each list
 */
class PopulateListItemsTransformationStep : ListsTransformationStep {

    private val logger = KotlinLogging.logger {}

    override fun execute(guestId: String, lists: List<ListGetAllResponseTO>, transformationContext: TransformationContext): Mono<List<ListGetAllResponseTO>> {
        if (lists.isNotEmpty()) {
            return lists.toFlux<ListGetAllResponseTO>().flatMap {
                val list: ListGetAllResponseTO = it
                getCartContents(list.listId, transformationContext).zipWith(getCartContents(list.completedListId, transformationContext))
                        .map {
                            val pendingListItems: List<ListItemResponseTO>? = it.t1.cartItems
                                    ?.map {
                                        toListItemResponseTO(it)
                                    }
                            val completedListItems: List<ListItemResponseTO>? = it.t2.cartItems
                                    ?.map {
                                        toListItemResponseTO(it)
                                    }
                            val pendingItemsCount = pendingListItems?.size ?: 0
                            val completedItemsCount = completedListItems?.size ?: 0

                            list.copy(pendingItems = pendingListItems, compeletedItems = completedListItems, pendingItemsCount = pendingItemsCount, completedItemsCount = completedItemsCount, totalItemsCount = pendingItemsCount + completedItemsCount)
                        }
            }.collectList()
        } else {
            return Mono.just(lists)
        }
    }

    private fun getCartContents(listId: UUID?, transformationContext: TransformationContext): Mono<CartContentsResponse> {
        if (listId == null) {
            return Mono.just(CartContentsResponse())
        }

        val listsTransformationPipelineConfiguration = transformationContext.transformationPipelineConfiguration as ListsTransformationPipelineConfiguration
        val cartManager = listsTransformationPipelineConfiguration.cartManager

        return Mono.subscriberContext().flatMap {
            val context = it

            cartManager.getListCartContents(listId, true)
                    .map { CartContentsResponse(cart = it.cart, cartItems = it.cartItems ?: arrayOf()) }
                    .onErrorResume {
                        logger.error("Error while getting cart contents for cart id $listId", it)
                        listsTransformationPipelineConfiguration.contextContainerManager.setPartialContentFlag(context)
                        Mono.just(CartContentsResponse())
                    }
        }
    }
}