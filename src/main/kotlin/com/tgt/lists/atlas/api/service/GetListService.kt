package com.tgt.lists.atlas.api.service

import com.tgt.lists.cart.transport.CartContentsResponse
import com.tgt.lists.common.components.exception.ResourceNotFoundException
import com.tgt.lists.atlas.api.domain.CartManager
import com.tgt.lists.atlas.api.domain.ContextContainerManager
import com.tgt.lists.atlas.api.service.transform.TransformationContext
import com.tgt.lists.atlas.api.service.transform.list_items.ListItemsTransformationPipeline
import com.tgt.lists.atlas.api.service.transform.list_items.ListItemsTransformationPipelineConfiguration
import com.tgt.lists.atlas.api.service.transform.list_items.PaginateListItemsTransformationStep.Companion.MAX_PAGE_COUNT
import com.tgt.lists.atlas.api.transport.ListItemResponseTO
import com.tgt.lists.atlas.api.transport.ListResponseTO
import com.tgt.lists.atlas.api.transport.toListItemResponseTO
import com.tgt.lists.atlas.api.transport.toListResponseTO
import com.tgt.lists.atlas.api.util.AppErrorCodes.RESOURCE_NOT_FOUND_ERROR_CODE
import com.tgt.lists.atlas.api.util.CartManagerName
import com.tgt.lists.atlas.api.util.Constants.LIST_ITEM_STATE_KEY
import com.tgt.lists.atlas.api.util.ItemIncludeFields
import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.atlas.api.util.LIST_ITEM_STATE
import io.micronaut.context.annotation.Value
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetListService(
    @CartManagerName("GetListService") @Inject private val cartManager: CartManager,
    @Inject private val listItemsTransformationPipelineConfiguration: ListItemsTransformationPipelineConfiguration,
    @Inject private val contextContainerManager: ContextContainerManager,
    @Value("\${list.features.two-carts}") private val isTwoCartsEnabled: Boolean
) {
    private val logger = KotlinLogging.logger {}

    @Value("\${list.max-pending-item-count}")
    private var maxPendingItemCount: Int = 100 // default max item count

    @Value("\${list.max-completed-items-count}")
    private var maxCompletedItemsCount: Int = 100 // default max item count

    fun getList(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        locationId: Long,
        listId: UUID,
        listItemsTransformationPipeline: ListItemsTransformationPipeline,
        includeItems: ItemIncludeFields
    ): Mono<ListResponseTO> {

        logger.debug("[getList] guestId: $guestId, listId: $listId, locationId: $locationId")

        return getCartContents(listId = listId, includeItems = includeItems)
                .flatMap { cartContentsResponses ->
                    val pendingCartContents = cartContentsResponses[0]
                    val completedCartContents = if (isTwoCartsEnabled) cartContentsResponses
                            .find { it.cart?.cartNumber == pendingCartContents.cart?.cartId.toString() } else null
                    toListResponseTO(listId, pendingCartContents,
                            completedCartContents, listItemsTransformationPipeline, includeItems)
                }
    }

    private fun toListResponseTO(
        listId: UUID,
        pendingCartContentsResponse: CartContentsResponse,
        completedCartContentsResponse: CartContentsResponse?,
        listItemsTransformationPipeline: ListItemsTransformationPipeline,
        includeItems: ItemIncludeFields
    ): Mono<ListResponseTO> {
        val cartResponse = pendingCartContentsResponse.cart!!

        return if (includeItems == ItemIncludeFields.PENDING) {
            // include only pending items
            val pendingListTransformedPair = transformListItems(listId, false, pendingCartContentsResponse, listItemsTransformationPipeline)
            pendingListTransformedPair.second.map {
                val maxPendingPageCount: Int? = try { pendingListTransformedPair.first.getContextValue(MAX_PAGE_COUNT) as Int } catch (e: Exception) { null }
                toListResponseTO(cartResponse,
                        pendingListItems = it, completedListItems = emptyList(),
                        maxPendingItemCount = maxPendingItemCount, maxCompletedItemsCount = maxCompletedItemsCount,
                        maxPendingPageCount = maxPendingPageCount)
            }
        } else if (includeItems == ItemIncludeFields.COMPLETED) {
            // include only completed items
            if (!isTwoCartsEnabled) {
                throw RuntimeException("Completed items not available for List: $listId with feature two-carts=false")
            }
            val completedListTransformedPair = transformListItems(listId, true, completedCartContentsResponse, listItemsTransformationPipeline)
            completedListTransformedPair.second.map {
                val maxCompletedPageCount: Int? = try { completedListTransformedPair.first.getContextValue(MAX_PAGE_COUNT) as Int } catch (e: Exception) { null }
                toListResponseTO(cartResponse,
                        pendingListItems = emptyList(), completedListItems = it,
                        maxPendingItemCount = maxPendingItemCount, maxCompletedItemsCount = maxCompletedItemsCount,
                        maxCompletedPageCount = maxCompletedPageCount)
            }
        } else if (includeItems == ItemIncludeFields.ALL) {
            // include both pending and completed items
            val pendingListTransformedPair = transformListItems(listId, false, pendingCartContentsResponse, listItemsTransformationPipeline)
            val completedListTransformedPair = transformListItems(listId, true, completedCartContentsResponse, listItemsTransformationPipeline)

            return pendingListTransformedPair.second.zipWith(completedListTransformedPair.second).map {
                val pendingItems: List<ListItemResponseTO> = it.t1
                val completedItems: List<ListItemResponseTO> = it.t2
                val maxPendingPageCount: Int? = try { pendingListTransformedPair.first.getContextValue(MAX_PAGE_COUNT) as Int } catch (e: Exception) { null }
                val maxCompletedPageCount: Int? = try { completedListTransformedPair.first.getContextValue(MAX_PAGE_COUNT) as Int } catch (e: Exception) { null }
                toListResponseTO(cartResponse, pendingListItems = pendingItems, completedListItems = completedItems,
                        maxPendingItemCount = maxPendingItemCount, maxCompletedItemsCount = maxCompletedItemsCount,
                        maxPendingPageCount = maxPendingPageCount, maxCompletedPageCount = maxCompletedPageCount)
            }
        } else {
            throw RuntimeException("Unsupported item include type: $includeItems")
        }
    }

    private fun transformListItems(
        listId: UUID,
        isCompleted: Boolean,
        cartContentsResponse: CartContentsResponse?,
        listItemsTransformationPipeline: ListItemsTransformationPipeline
    ): Pair<TransformationContext, Mono<List<ListItemResponseTO>>> {

        val transformationContext = TransformationContext(transformationPipelineConfiguration = listItemsTransformationPipelineConfiguration)
        transformationContext.addContextValue(LIST_ITEM_STATE_KEY, if (isCompleted) LIST_ITEM_STATE.COMPLETED else LIST_ITEM_STATE.PENDING)

        val listItemsTransformed = cartContentsResponse?.let {
            val listItems: List<ListItemResponseTO>? = it.cartItems
                    ?.map {
                        toListItemResponseTO(it)
                    }
            listItems?.let {
                listItemsTransformationPipeline.executePipeline(listId, listItems, transformationContext)
            }
        } ?: Mono.just(emptyList())

        return Pair(transformationContext, listItemsTransformed)
    }

    private fun isOfferItem(itemType: ItemType?): Boolean {
        return itemType != null && (itemType == ItemType.OFFER)
    }

    private fun getCartContents(
        listId: UUID,
        includeItems: ItemIncludeFields
    ): Mono<List<CartContentsResponse>> {
        return Mono.subscriberContext().flatMap {
            val context = it
            var pendingContents = makeCartContentsReq(listId = listId, context = context)
            var completedContents: Mono<CartContentsResponse>? = null

            if (includeItems == ItemIncludeFields.COMPLETED || includeItems == ItemIncludeFields.ALL) {
                completedContents = cartManager.getCompletedListCart(listId = listId)
                        .flatMap { makeCartContentsReq(listId = it.cartId, context = context) }
                        .onErrorResume {
                            logger.error("Exception while searching completed carts for get list by id $listId", it)
                            contextContainerManager.setPartialContentFlag(context)
                            Mono.just(CartContentsResponse())
                        }
                        .switchIfEmpty(
                                Mono.fromCallable {
                                    logger.error("Completed cart not found for get list by id $listId", it)
                                    contextContainerManager.setPartialContentFlag(context)
                                    CartContentsResponse()
                                }
                        )
            }

            if (completedContents != null) {
                pendingContents.zipWith(completedContents).map {
                                        val pending = it.t1
                                        val completed = it.t2
                                        listOf<CartContentsResponse>(pending, completed)
                }
            } else {
                pendingContents.map {
                    listOf<CartContentsResponse>(it, CartContentsResponse())
                }
            }
        }
    }

    private fun makeCartContentsReq(
        listId: UUID?,
        context: reactor.util.context.Context,
        fail: Boolean = true
    ): Mono<CartContentsResponse> {
        if (listId == null) {
            return Mono.just(CartContentsResponse())
        }

        return cartManager.getListCartContents(listId, true)
            .map {
                CartContentsResponse(it.summary, it.cart, it.cartItems ?: arrayOf())
            }
            .onErrorResume {
                logger.error("Exception while getting " +
                    "cart contents for get list by id $listId", it)
                if (fail) {
                    throw it
                }
                contextContainerManager.setPartialContentFlag(context)
                Mono.just(CartContentsResponse())
            }.switchIfEmpty(if (fail) Mono.error(ResourceNotFoundException(RESOURCE_NOT_FOUND_ERROR_CODE(listOf("List $listId not found"))))
            else Mono.just(CartContentsResponse()))
    }
}
