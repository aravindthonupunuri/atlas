package com.tgt.lists.atlas.api.service

import com.tgt.lists.cart.transport.CartContentsResponse
import com.tgt.lists.cart.transport.CartPutRequest
import com.tgt.lists.cart.transport.CartResponse
import com.tgt.lists.common.components.exception.BadRequestException
import com.tgt.lists.atlas.api.domain.CartManager
import com.tgt.lists.atlas.api.domain.DefaultListManager
import com.tgt.lists.atlas.api.domain.UpdateCartManager
import com.tgt.lists.atlas.api.transport.*
import com.tgt.lists.atlas.api.util.*
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateListService(
    @CartManagerName("UpdateListService") @Inject private val cartManager: CartManager,
    @Inject private val defaultListManager: DefaultListManager,
    @Inject private val updateCartManager: UpdateCartManager
) {
    private val logger = KotlinLogging.logger {}

    fun updateList(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        listId: UUID,
        listUpdateRequestTO: ListUpdateRequestTO
    ): Mono<ListResponseTO> {

        logger.debug("[updateList] guestId: $guestId, listId: $listId")

        return defaultListManager.processDefaultListInd(guestId, listUpdateRequestTO.validate().defaultList ?: false, listId)
            .flatMap { processListUpdate(guestId, listId, listUpdateRequestTO) }
    }

    private fun processListUpdate(guestId: String, listId: UUID, listUpdateRequestTO: ListUpdateRequestTO): Mono<ListResponseTO> {
        return cartManager.getListCartContents(listId, false)
                .flatMap { toCartPutRequest(it, listUpdateRequestTO) }
                .flatMap { updateCartManager.updateCart(guestId = guestId, cartId = listId, cartPutRequest = it) }
                .map { toListResponseTO(it) }
    }

    private fun toCartPutRequest(cartContentsResponse: CartContentsResponse, listUpdateRequest: ListUpdateRequestTO): Mono<CartPutRequest> {
        val cartResponse = cartContentsResponse.cart ?: throw BadRequestException(AppErrorCodes.BAD_REQUEST_ERROR_CODE(listOf("Cart not found")))
        val cartMetadata = cartResponse.metadata
        val listMetadata = getListMetaDataFromCart(cartMetadata)
        val userMetadata = getUserMetaDataFromCart(cartMetadata)
        userMetadata?.let { listUpdateRequest.userMetaDataTransformationStep?.let {
            return it.execute(userMetadata)
                    .map {
                        val metaData = it
                        createCartPutRequest(listUpdateRequest, listMetadata, metaData, cartResponse)
                    }
        } }

        return Mono.just(createCartPutRequest(listUpdateRequest, listMetadata, userMetadata, cartResponse))
    }

    private fun createCartPutRequest(listUpdateRequest: ListUpdateRequestTO, listMetadata: ListMetaDataTO, metaData: UserMetaDataTO?, cartResponse: CartResponse): CartPutRequest {
        val updatedListMetaData = setCartMetaDataFromList(
                defaultList = listUpdateRequest.defaultList ?: (listMetadata.defaultList),
                tenantMetaData = metaData?.userMetaData
        )

        return CartPutRequest(
                tenantCartName = listUpdateRequest.listTitle ?: cartResponse.tenantCartName,
                tenantCartDescription = listUpdateRequest.shortDescription ?: cartResponse.tenantCartDescription,
                metadata = updatedListMetaData)
    }
}
