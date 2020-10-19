package com.tgt.lists.atlas.api.service


import com.tgt.lists.cart.transport.CartDeleteRequest
import com.tgt.lists.cart.transport.CartDeleteResponse
import com.tgt.lists.cart.transport.CartResponse
import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.atlas.api.domain.CartManager
import com.tgt.lists.atlas.api.domain.EventPublisher
import com.tgt.lists.atlas.api.transport.ListMetaDataTO
import com.tgt.lists.atlas.api.transport.UserMetaDataTO
import com.tgt.lists.atlas.api.util.LIST_STATUS
import com.tgt.lists.atlas.kafka.model.DeleteListNotifyEvent
import com.tgt.lists.atlas.util.CartDataProvider
import com.tgt.lists.atlas.util.TestListChannel
import org.apache.kafka.clients.producer.RecordMetadata
import reactor.core.publisher.Mono
import spock.lang.Specification

class DeleteListServiceTest extends Specification {
    
    CartManager cartManager
    EventPublisher eventPublisher
    DeleteListService deleteListService
    CartDataProvider cartDataProvider
    String guestId = "1234"

    def setup() {
        cartManager = Mock(CartManager)
        eventPublisher = Mock(EventPublisher)
        deleteListService = new DeleteListService(cartManager, eventPublisher)
        cartDataProvider = new CartDataProvider()
    }

    def "Test deleteListService when the completed cart is not present"() {
        given:
        UUID listId = UUID.randomUUID()
        CartDeleteResponse pendingDeleteResponse = cartDataProvider.getCartDeleteResponse(listId)
        CartDeleteRequest pendingCartDeleteRequest = cartDataProvider.getCartDeleteRequest(listId, false)
        ListMetaDataTO pendingCartMetadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        CartResponse pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), "SHOPPING", CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(pendingCartMetadata, new UserMetaDataTO()))

        when:
        def actual = deleteListService.deleteList(guestId,listId).block()

        then:
        1 * cartManager.deleteCart(pendingCartDeleteRequest) >> Mono.just(pendingDeleteResponse)
        1 * cartManager.getAllCarts(guestId, _) >> Mono.just([pendingCartResponse])
        1 * eventPublisher.publishEvent(DeleteListNotifyEvent.getEventType(), _ , guestId) >>  Mono.just(GroovyMock(RecordMetadata))
        actual.listId == listId
    }

    def "Test deleteListService when the completed cart is present"() {
        given:
        UUID listId = UUID.randomUUID()
        UUID completedListId = UUID.randomUUID()
        CartDeleteResponse pendingDeleteResponse = cartDataProvider.getCartDeleteResponse(listId)
        CartDeleteRequest pendingCartDeleteRequest = cartDataProvider.getCartDeleteRequest(listId, false)
        CartDeleteRequest completedCartDeleteRequest = cartDataProvider.getCartDeleteRequest(completedListId, false)
        CartDeleteResponse completedDeleteResponse = cartDataProvider.getCartDeleteResponse(completedListId)
        ListMetaDataTO pendingCartMetadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        CartResponse pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId,
            TestListChannel.WEB.toString(), "SHOPPING", CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(pendingCartMetadata, new UserMetaDataTO()))
        ListMetaDataTO completedCartMetadata = new ListMetaDataTO(false, LIST_STATUS.COMPLETED)
        CartResponse completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, "SHOPPING", listId.toString(),
            cartDataProvider.getMetaData(completedCartMetadata, new UserMetaDataTO()))
        def recordMetadata = GroovyMock(RecordMetadata)

        when:
        def actual = deleteListService.deleteList(guestId,listId).block()

        then:
        1 * cartManager.deleteCart(pendingCartDeleteRequest) >> Mono.just(pendingDeleteResponse)
        1 * cartManager.deleteCart(completedCartDeleteRequest) >> Mono.just(completedDeleteResponse)
        1 * eventPublisher.publishEvent(DeleteListNotifyEvent.getEventType(), _ , guestId) >>  Mono.just(recordMetadata)
        1 * eventPublisher.publishEvent(DeleteListNotifyEvent.getEventType(), _ , guestId) >>  Mono.just(recordMetadata)
        1 * cartManager.getAllCarts(guestId, _) >> Mono.just([completedCartResponse, pendingCartResponse])
        actual.listId == listId
    }

    def "Test deleteListService when getting cart fails"() {
        given:
        UUID listId = UUID.randomUUID()

        when:
        deleteListService.deleteList(guestId,listId).block()

        then:
        1 * cartManager.getAllCarts(guestId, _) >> Mono.error(new RuntimeException("some exception"))
        thrown(RuntimeException)
    }

    def "Test deleteListService when no completed and pending"() {
        given:
        UUID listId = UUID.randomUUID()

        when:
        def actual = deleteListService.deleteList(guestId,listId).block()

        then:
        1 * cartManager.getAllCarts(guestId, _) >> Mono.empty()
        actual.listId == listId
    }
}
