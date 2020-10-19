package com.tgt.lists.atlas.api.domain

import com.tgt.lists.cart.CartClient
import com.tgt.lists.cart.transport.*
import com.tgt.lists.cart.types.CartContentsFieldGroups
import com.tgt.lists.cart.types.SearchCartsFieldGroup
import com.tgt.lists.cart.types.SearchCartsFieldGroups
import com.tgt.lists.common.components.exception.BaseErrorCodes
import com.tgt.lists.common.components.exception.ResourceNotFoundException
import com.tgt.lists.atlas.api.util.CartManagerName
import com.tgt.lists.atlas.api.util.LIST_STATUS
import com.tgt.lists.atlas.api.util.getListMetaDataFromCart
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Value
import io.micronaut.inject.InjectionPoint
import mu.KotlinLogging
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.util.*
import javax.inject.Inject

/**
 * Read only cart manager
 */
@Prototype
class CartManager(
    @Inject private val injectionPoint: InjectionPoint<CartManagerName>,
    @Inject private val cartClient: CartClient,
    @Value("\${list.list-type}") private val listType: String,
    @Value("\${list.features.two-carts}") private val isTwoCartsEnabled: Boolean
) {
    private val logger = KotlinLogging.logger {}

    private val cartSearchFieldGroups = SearchCartsFieldGroups(arrayListOf(SearchCartsFieldGroup.CART))
    private val cartContentsFieldGroup = CartContentsFieldGroups(arrayListOf(CartContentsFieldGroup.CART))
    private val cartItemsContentsFieldGroup = CartContentsFieldGroups(arrayListOf(CartContentsFieldGroup.CART, CartContentsFieldGroup.CARTITEMS, CartContentsFieldGroup.CARTMETADATA))

    private val managerName: String
    init {
        managerName = injectionPoint.annotationMetadata.stringValue(CartManagerName::class.java).orElse("")
        if (managerName.isEmpty()) {
            throw RuntimeException("Manager Name is required")
        }
    }

    /*-- GET carts and cartItems --- */

    fun getAllCarts(
        guestId: String,
        fieldGroups: SearchCartsFieldGroups = cartSearchFieldGroups
    ): Mono<List<CartResponse>> {

        logger.debug("[$managerName.getAllCarts] guestId: $guestId")

        return cartClient.searchCarts(guestId = guestId, cartState = CartState.PENDING, cartType = CartType.LIST, cartSubchannel = listType, fieldGroups = fieldGroups)
                .switchIfEmpty { Mono.just(arrayListOf()) }
                .map {
                    it.filter {
                        it.cartSubchannel == listType
                    }
            }
    }

    fun getAllPendingCarts(guestId: String): Mono<List<CartResponse>> {

        logger.debug("[$managerName.getAllPendingCarts] guestId: $guestId")

        return getAllCarts(guestId = guestId).map {
            it.filter {
                val listMetadata = getListMetaDataFromCart(it.metadata)
                listMetadata.listStatus == LIST_STATUS.PENDING
            }
        }
    }

    fun getListCartContents(listId: UUID, withItems: Boolean = false): Mono<CartContentsResponse> {

        logger.debug("[$managerName.getListCartContents] listId: $listId, withItems: $withItems")

        return if (withItems) cartClient.getCartContents(cartId = listId, fieldGroups = cartItemsContentsFieldGroup) else cartClient.getCartContents(cartId = listId, fieldGroups = cartContentsFieldGroup)
    }

    fun getCompletedListCartContents(listId: UUID, withItems: Boolean = false): Mono<CartContentsResponse> {

        logger.debug("[$managerName.getCompletedListCartContents] listId: $listId, withItems: $withItems")

        if (!isTwoCartsEnabled) {
            throw RuntimeException("[$managerName] Completed List Cart Content is not available when feature two-carts=false")
        }

        return if (withItems) cartClient.getCartContents(cartId = listId, fieldGroups = cartItemsContentsFieldGroup) else cartClient.getCartContents(cartId = listId, fieldGroups = cartContentsFieldGroup)
    }

    fun getCompletedListCart(listId: UUID): Mono<CartResponse> {

        logger.debug("[$managerName.getCompletedListCart] listId: $listId")

        if (!isTwoCartsEnabled) {
            throw RuntimeException("[$managerName] Completed Cart is not available when feature two-carts=false")
        }

        return cartClient.getCartContents(cartId = listId, fieldGroups = cartContentsFieldGroup)
                .switchIfEmpty {
                    throw ResourceNotFoundException(BaseErrorCodes.RESOURCE_NOT_FOUND_ERROR_CODE(listOf("List $listId not found")))
                }
                .flatMap {
                    cartClient.searchCarts(it.cart!!.guestId!!, listId.toString(), CartState.PENDING, null, listType, CartType.LIST, cartSearchFieldGroups)
                            .flatMap {
                                if (it.isNullOrEmpty()) {
                                    Mono.empty<CartResponse>()
                                } else {
                                    Mono.just(it.first())
                                }
                            }
                }
    }

    fun getItemInCompletedCart(
        listId: UUID,
        listItemId: UUID
    ): Mono<CartItemResponse> {
        logger.debug("[$managerName.getItemInCompletedCart] listId: $listId, listItemId: $listItemId")
        return getCompletedListCart(listId).flatMap { getCartItem(it.cartId!!, listItemId) }
    }

    fun getCartItem(cartId: UUID, cartItemId: UUID): Mono<CartItemResponse> {
        logger.debug("[$managerName.getCartItem] cartId: $cartId, itemId: $cartItemId")
        return cartClient.getCartItem(cartItemId = cartItemId, cartId = cartId)
    }

    /*-- CREATE carts and cartItems --- */

    fun createCart(cartPostRequest: CartPostRequest): Mono<CartResponse> {
        logger.debug("[$managerName.createCart] cartPostRequest: $cartPostRequest")
        return cartClient.createCart(cartPostRequest = cartPostRequest)
    }

    fun createCartItem(cartItemsRequest: CartItemsRequest): Mono<CartItemResponse> {
        logger.debug("[$managerName.createCartItem] cartItemsRequest: $cartItemsRequest")
        return cartClient.createCartItem(cartItemsRequest = cartItemsRequest)
    }

    fun addMultiCartItems(addMultiCartItemsRequest: AddMultiCartItemsRequest, existingCartItemIds: Set<UUID>):
            Mono<List<CartItemResponse>> {
        logger.debug("[$managerName.addMultiCartItems] addMultiCartItemsRequest: $addMultiCartItemsRequest")
        if (addMultiCartItemsRequest.cartItems.size > 1) {
            return cartClient.addMultiCartItems(fieldGroups = cartItemsContentsFieldGroup,
                    addMultiCartItemsRequest = addMultiCartItemsRequest,
                    refresh = false)
                    .map {
                        // Multi add item cart end point in its response gives the complete list of cart items in the cart
                        // and not just the items being added. So we need to filter out existing items from the response
                        // to get the newly added items.
                        val cartItems = it.cartItems?.toList() ?: emptyList()
                        val createdCartItems = cartItems.filter {
                            existingCartItemIds.contains(it.cartItemId)
                        }
                        createdCartItems
                    }
        } else {
            // Using single add item cart end point in case only one cart item is being added
            return createCartItem(addMultiCartItemsRequest.cartItems.first()).map { listOf(it) }
        }
    }

    /*-- UPDATE carts and cartItems --- */

    fun updateCart(cartId: UUID, cartPutRequest: CartPutRequest): Mono<CartResponse> {
        logger.debug("[$managerName.updateCart] cartId: $cartId, cartPutRequest: $cartPutRequest")
        return cartClient.updateCart(cartId = cartId, cartPutRequest = cartPutRequest)
    }

    fun updateCartItem(cartItemId: UUID, cartItemUpdateRequest: CartItemUpdateRequest): Mono<CartItemResponse> {
        logger.debug("[$managerName.updateCartItem] cartItemId: $cartItemId, cartItemUpdateRequest: $cartItemUpdateRequest")
        return cartClient.updateCartItem(cartItemId = cartItemId, cartItemUpdateRequest = cartItemUpdateRequest)
    }

    fun updateMultiCartItems(updateMultiCartItemsUpdateRequest: UpdateMultiCartItemsRequest): Mono<CartContentsResponse> {
        logger.debug("[$managerName.updateMultiCartItems] updateMultiCartItemsUpdateRequest: $updateMultiCartItemsUpdateRequest")
        return cartClient.updateMultiCartItems(fieldGroups = cartItemsContentsFieldGroup, updateMultiCartItemsUpdateRequest = updateMultiCartItemsUpdateRequest)
    }

    /*-- DELETE carts and cartItems --- */

    fun deleteCart(cartDeleteRequest: CartDeleteRequest): Mono<CartDeleteResponse> {
        logger.debug("[$managerName.deleteCart] cartDeleteRequest: $cartDeleteRequest")
        return cartClient.deleteCart(cartDeleteRequest = cartDeleteRequest)
    }

    fun deleteCartItem(deleteCartItemRequest: DeleteCartItemRequest): Mono<CartItemDeleteResponse> {
        logger.debug("[$managerName.deleteCartItem] deleteCartItemRequest: $deleteCartItemRequest")
        return cartClient.deleteCartItem(deleteCartItemRequest = deleteCartItemRequest)
    }

    fun deleteMultiCartItems(deleteMultiCartItemsRequest: DeleteMultiCartItemsRequest): Mono<DeleteMultiCartItemsResponse> {
        logger.debug("[$managerName.deleteMultiCartItems] deleteMultiCartItemsRequest: $deleteMultiCartItemsRequest")
        return cartClient.deleteMultiCartItems(deleteMultiCartItemsRequest = deleteMultiCartItemsRequest)
    }
}
