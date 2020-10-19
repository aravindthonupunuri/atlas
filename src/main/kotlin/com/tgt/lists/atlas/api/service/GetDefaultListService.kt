package com.tgt.lists.atlas.api.service

import com.tgt.lists.cart.transport.CartResponse
import com.tgt.lists.atlas.api.domain.CartManager
import com.tgt.lists.atlas.api.service.transform.list_items.ListItemsTransformationPipeline
import com.tgt.lists.atlas.api.transport.ListResponseTO
import com.tgt.lists.atlas.api.util.CartManagerName
import com.tgt.lists.atlas.api.util.ItemIncludeFields
import com.tgt.lists.atlas.api.util.getListMetaDataFromCart
import io.micronaut.context.annotation.Value
import mu.KotlinLogging
import reactor.core.publisher.Mono
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetDefaultListService(
    @CartManagerName("GetDefaultListService") @Inject private val cartManager: CartManager,
    @Inject private val getListService: GetListService,
    @Value("\${list.list-type}") private val listType: String
) {
    private val logger = KotlinLogging.logger {}

    fun getDefaultList(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        locationId: Long,
        listItemsTransformationPipeline: ListItemsTransformationPipeline,
        includeItems: ItemIncludeFields
    ): Mono<ListResponseTO> {

        logger.debug("[getDefaultList] guestId: $guestId, locationId: $locationId")

        return cartManager.getAllCarts(guestId = guestId)
            .flatMap {
                processGetDefaultList(it, guestId, locationId, listItemsTransformationPipeline, includeItems)
            }
    }

    fun processGetDefaultList(
        cartResponseList: List<CartResponse>,
        guestId: String,
        locationId: Long,
        listItemsTransformationPipeline: ListItemsTransformationPipeline,
        includeItems: ItemIncludeFields
    ): Mono<ListResponseTO> {
        if (cartResponseList.isEmpty()) { return Mono.empty() }
        val defaultListId = cartResponseList.first { cartResponse ->
            val metadata = getListMetaDataFromCart(cartResponse.metadata)
            metadata.defaultList && listType == cartResponse.cartSubchannel
        }.cartId
        return if (defaultListId != null) {
            getListService.getList(guestId, locationId, defaultListId, listItemsTransformationPipeline, includeItems)
        } else {
            Mono.empty()
        }
    }
}
