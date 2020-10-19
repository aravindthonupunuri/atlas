package com.tgt.lists.atlas.api.domain

import com.tgt.lists.cart.transport.CartPutRequest
import com.tgt.lists.cart.transport.CartResponse
import com.tgt.lists.common.components.exception.BadRequestException
import com.tgt.lists.atlas.api.util.*
import io.micronaut.context.annotation.Value
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultListManager(
    @CartManagerName("DefaultListManager") @Inject private val cartManager: CartManager,
    @Inject private val updateCartManager: UpdateCartManager,
    @Value("\${list.max-count}") private val maxListsCount: Int = 50,
    @Value("\${list.features.fixed-default-list}") private val isFixedDefaultListEnabled: Boolean
) {
    fun processDefaultListInd(guestId: String, defaultListIndicator: Boolean, listId: UUID? = null): Mono<Boolean> {
        // this method can be called either during create new list (listId=null)or during update if an existing list (listId NOT null)
        // create list is always called with the guestId as ownerId for new list
        // whereas update may be called with guestId as operation executor who could be different than list owner
        // hence we should always get owner of list for update scenario before we proceed
        val listGuestId = listId?.let {
            cartManager.getListCartContents(listId).map {
                it.cart!!.guestId!!
            }
        } ?: Mono.just(guestId)

        return listGuestId.flatMap {
            cartManager.getAllCarts(guestId = it).map { checkGuestListsCount(it, listId) }
                    .flatMap { setDefaultList(it, guestId, defaultListIndicator, listId) }
        }
    }

    fun checkGuestListsCount(cartResponseList: List<CartResponse>, listId: UUID?): List<CartResponse> {
        if (listId != null) { return cartResponseList } // From update, so do not check for max lists count
        val count = cartResponseList.filter {
            val cartMetadata = it.metadata
            val listMetadata = getListMetaDataFromCart(cartMetadata)
            listMetadata.listStatus == LIST_STATUS.PENDING
        }.count()

        if (count >= maxListsCount) {
            throw BadRequestException(AppErrorCodes.LIST_MAX_COUNT_VIOLATION_ERROR_CODE(arrayListOf("Max guests lists reached")))
        }

        return cartResponseList
    }

    fun setDefaultList(
        cartResponseList: List<CartResponse>,
        guestId: String,
        defaultListIndicator: Boolean,
        listId: UUID?
    ): Mono<Boolean> {
        val defaultListCarts = getDefaultListCarts(cartResponseList, listId)
        if (defaultListCarts.isEmpty()) {
            return Mono.just(true) // No preexisting carts with default list found
        }
        if (isFixedDefaultListEnabled) {
            return Mono.just(false)
        }

        if (defaultListIndicator) {
            return Flux.fromIterable(defaultListCarts.asIterable())
                .flatMap { cartResponse ->
                    val cartMetadata = cartResponse.metadata
                    val listMetadata = getListMetaDataFromCart(cartMetadata)
                    val userMetadata = getUserMetaDataFromCart(cartMetadata)
                    if (listMetadata.defaultList) {
                        val updateListMetaData = setCartMetaDataFromList(
                            defaultList = false,
                            tenantMetaData = userMetadata?.userMetaData)
                        updateCartManager.updateCart(guestId = guestId, cartId = cartResponse.cartId!!,
                            cartPutRequest = CartPutRequest(tenantCartName = cartResponse.tenantCartName, metadata = updateListMetaData))
                    } else {
                        Mono.just(cartResponse)
                    }
                }.then(Mono.just(defaultListIndicator))
        } else {
            return Mono.just(defaultListIndicator)
        }
    }

    fun getDefaultListCarts(cartResponseList: List<CartResponse>, listId: UUID?): List<CartResponse> {
        return cartResponseList
            .filter {
                val cartMetadata = it.metadata
                val listMetadata = getListMetaDataFromCart(cartMetadata)
                listMetadata.listStatus == LIST_STATUS.PENDING && listMetadata.defaultList && (listId == null || it.cartId != listId)
            }
    }
}
