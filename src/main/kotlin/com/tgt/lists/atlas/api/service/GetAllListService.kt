package com.tgt.lists.atlas.api.service

import com.tgt.lists.cart.transport.CartResponse
import com.tgt.lists.atlas.api.domain.CartManager
import com.tgt.lists.atlas.api.service.transform.TransformationContext
import com.tgt.lists.atlas.api.service.transform.list.ListsTransformationPipeline
import com.tgt.lists.atlas.api.service.transform.list.ListsTransformationPipelineConfiguration
import com.tgt.lists.atlas.api.transport.ListGetAllResponseTO
import com.tgt.lists.atlas.api.util.CartManagerName
import com.tgt.lists.atlas.api.util.LIST_STATUS
import com.tgt.lists.atlas.api.util.getListMetaDataFromCart
import com.tgt.lists.atlas.api.util.getUserMetaDataFromCart
import io.micronaut.context.annotation.Value
import mu.KotlinLogging
import reactor.core.publisher.Mono
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetAllListService(
    @CartManagerName("GetAllListService") @Inject private val cartManager: CartManager,
    @Inject private val listsTransformationPipelineConfiguration: ListsTransformationPipelineConfiguration,
    @Value("\${list.list-type}") private val listType: String,
    @Value("\${list.max-count}") private val maxListsCount: Int,
    @Value("\${list.features.two-carts}") private val isTwoCartsEnabled: Boolean
) {
    private val logger = KotlinLogging.logger {}

    fun getAllListsForUser(
        guestId: String, // this is NOT the ownerId of list, it represents operation executor who could be different than list owner
        listsTransformationPipeline: ListsTransformationPipeline?
    ): Mono<List<ListGetAllResponseTO>> {

        logger.debug("[getAllListsForUser] guestId: $guestId")

        return cartManager.getAllCarts(guestId = guestId)
                    .flatMap { process(guestId, it, listsTransformationPipeline) }
    }

    private fun process(
        guestId: String,
        carts: List<CartResponse>,
        listsTransformationPipeline: ListsTransformationPipeline?
    ): Mono<List<ListGetAllResponseTO>> {
        val listOfLists = carts
                .map {
                    val listMetadata = getListMetaDataFromCart(it.metadata)
                    Pair(it, listMetadata)
                }
                .filter {
                    val listMetadata = it.second
                    listMetadata.listStatus == LIST_STATUS.PENDING
                }.map {
                    val pendingCartResponse = it.first
                    val listMetadata = it.second
                    val completedCartResponse = if (isTwoCartsEnabled) carts.find { it.cartNumber.equals(pendingCartResponse.cartId.toString()) } else null

                    ListGetAllResponseTO(
                            listId = pendingCartResponse.cartId,
                            completedListId = completedCartResponse?.cartId,
                            channel = pendingCartResponse.cartChannel,
                            listType = pendingCartResponse.cartSubchannel,
                            listTitle = pendingCartResponse.tenantCartName,
                            shortDescription = pendingCartResponse.tenantCartDescription,
                            agentId = pendingCartResponse.agentId,
                            metadata = getUserMetaDataFromCart(pendingCartResponse.metadata)?.userMetaData,
                            defaultList = listMetadata.defaultList,
                            maxListsCount = maxListsCount,
                            addedTs = pendingCartResponse.createdAt.let { it.toString() + "Z" },
                            lastModifiedTs = pendingCartResponse.updatedAt.let { it.toString() + "Z" }
                    )
                }

        return listsTransformationPipeline?.let {
            val transformationContext = TransformationContext(transformationPipelineConfiguration = listsTransformationPipelineConfiguration)
            it.executePipeline(guestId = guestId, lists = listOfLists, transformationContext = transformationContext)
        } ?: Mono.just(listOfLists)
    }
}
