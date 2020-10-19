package com.tgt.lists.atlas.api.service


import com.tgt.lists.cart.transport.CartPutRequest
import com.tgt.lists.cart.transport.CartResponse
import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.common.components.exception.BadRequestException
import com.tgt.lists.atlas.api.domain.CartManager
import com.tgt.lists.atlas.api.domain.DefaultListManager
import com.tgt.lists.atlas.api.domain.EventPublisher
import com.tgt.lists.atlas.api.domain.UpdateCartManager
import com.tgt.lists.atlas.api.transport.ListMetaDataTO
import com.tgt.lists.atlas.api.transport.ListUpdateRequestTO
import com.tgt.lists.atlas.api.transport.UserMetaDataTO
import com.tgt.lists.atlas.api.util.LIST_STATUS
import com.tgt.lists.atlas.kafka.model.UpdateListNotifyEvent
import com.tgt.lists.atlas.util.CartDataProvider
import com.tgt.lists.atlas.util.TestListChannel
import org.apache.kafka.clients.producer.RecordMetadata
import reactor.core.publisher.Mono
import spock.lang.Specification

class UpdateListServiceTest extends Specification {

    UpdateListService updateListService
    UpdateCartManager updateCartManager
    DefaultListManager defaultListManager
    EventPublisher eventPublisher
    CartManager cartManager
    CartDataProvider cartDataProvider
    String guestId = "1234"

    def setup() {
        cartManager = Mock(CartManager)
        eventPublisher = Mock(EventPublisher)
        updateCartManager = new UpdateCartManager(cartManager, eventPublisher)
        defaultListManager = new DefaultListManager(cartManager, updateCartManager, 10, false)
        updateListService = new UpdateListService(cartManager, defaultListManager, updateCartManager)
        cartDataProvider = new CartDataProvider()
    }

    def "Test updateList() integrity"() {
        given:
        def ListUpdateRequestTO = new ListUpdateRequestTO("updatedTitle", "updated description", true, null, null)

        UUID listId = UUID.randomUUID()

        ListMetaDataTO cartMetadata = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse cartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "title", "short description", null, cartDataProvider.getMetaData(cartMetadata, new UserMetaDataTO()))
        List<CartResponse> cartResponseList = [cartResponse]
        def cartContents = cartDataProvider.getCartContentsResponse(cartResponse, [])

        ListMetaDataTO updatedCartMetadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        CartResponse updatedCartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(),"SHOPPING", CartType.LIST,"updatedTitle", "updated description",
            null, cartDataProvider.getMetaData(updatedCartMetadata, new UserMetaDataTO()))

        def cartContentResponse = cartDataProvider.getCartContentsResponse(cartResponse, [])

        when:
        def actual = updateListService.updateList(guestId, listId, ListUpdateRequestTO).block()

        then:
        1 * cartManager.getListCartContents(listId, false) >> Mono.just(cartContentResponse)
        1 * cartManager.getAllCarts(guestId, _) >> Mono.just(cartResponseList)
        1 * cartManager.getListCartContents(listId, false) >> Mono.just(cartContents)
        1 * cartManager.updateCart(listId, _ as CartPutRequest) >> { arguments ->
            final CartPutRequest request = arguments[1]
            assert request.tenantCartName == "updatedTitle"
            assert request.tenantCartDescription == "updated description"
            Mono.just(updatedCartResponse)
        }
        1 * eventPublisher.publishEvent(UpdateListNotifyEvent.eventType, _, _) >> Mono.just(GroovyMock(RecordMetadata))

        actual.listId == updatedCartResponse.cartId
        actual.channel == updatedCartResponse.cartChannel
        actual.listTitle == updatedCartResponse.tenantCartName
        actual.shortDescription == updatedCartResponse.tenantCartDescription
        actual.listType == updatedCartResponse.cartSubchannel
        actual.defaultList == updatedCartMetadata.defaultList
    }

    def "Test updateList() with null field"() {
        given:
        def ListUpdateRequestTO = new ListUpdateRequestTO("updatedTitle", null, true, null, null)

        UUID listId = UUID.randomUUID()

        ListMetaDataTO cartMetadata = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse cartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(),"SHOPPING", CartType.LIST, "title", "short description", null, cartDataProvider.getMetaData(cartMetadata, new UserMetaDataTO()))
        List<CartResponse> cartResponseList = [cartResponse]
        def cartContents = cartDataProvider.getCartContentsResponse(cartResponse, [])

        ListMetaDataTO updatedCartMetadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        CartResponse updatedCartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), "SHOPPING", CartType.LIST, "updatedTitle", "short description",
            null, cartDataProvider.getMetaData(updatedCartMetadata, new UserMetaDataTO()))

        when:
        def actual = updateListService.updateList(guestId, listId, ListUpdateRequestTO).block()

        then:
        1 * cartManager.getListCartContents(listId, false) >> Mono.just(cartContents)
        1 * cartManager.getAllCarts(guestId, _) >> Mono.just(cartResponseList)
        1 * cartManager.getListCartContents(listId, false) >> Mono.just(cartContents)
        1 * cartManager.updateCart(listId, _ as CartPutRequest) >> { arguments ->
            final CartPutRequest request = arguments[1]
            assert request.tenantCartName == "updatedTitle"
            assert request.tenantCartDescription == "short description"
            Mono.just(updatedCartResponse)
        }
        1 * eventPublisher.publishEvent(UpdateListNotifyEvent.eventType, _, _) >> Mono.just(GroovyMock(RecordMetadata))


        actual.listId == updatedCartResponse.cartId
        actual.channel == updatedCartResponse.cartChannel
        actual.listTitle == updatedCartResponse.tenantCartName
        actual.shortDescription == cartResponse.tenantCartDescription
        actual.listType == cartResponse.cartSubchannel
        actual.defaultList == updatedCartMetadata.defaultList
    }

    def "Test updateList() with false default List value"() {
        given:
        def ListUpdateRequestTO = new ListUpdateRequestTO("updatedTitle", null, false, null, null)
        UUID listId = UUID.randomUUID()

        when:
        updateListService.updateList(guestId, listId, ListUpdateRequestTO).block()

        then:
        thrown(BadRequestException)
    }
}
