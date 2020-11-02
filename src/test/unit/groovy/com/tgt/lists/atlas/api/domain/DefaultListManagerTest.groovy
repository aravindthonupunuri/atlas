//package com.tgt.lists.atlas.api.domain
//
//import com.datastax.oss.driver.api.core.uuid.Uuids
//import com.tgt.lists.atlas.api.transport.ListMetaDataTO
//import com.tgt.lists.atlas.api.transport.UserMetaDataTO
//import com.tgt.lists.atlas.api.util.LIST_STATUS
//import com.tgt.lists.atlas.kafka.model.UpdateListNotifyEvent
//import com.tgt.lists.atlas.util.CartDataProvider
//import com.tgt.lists.cart.transport.CartPutRequest
//import com.tgt.lists.cart.transport.CartResponse
//import com.tgt.lists.common.components.exception.BadRequestException
//import org.apache.kafka.clients.producer.RecordMetadata
//import reactor.core.publisher.Mono
//import spock.lang.Specification
//
//class DefaultListManagerTest extends Specification {
//
//    DefaultListManager defaultListManager
//    CartManager cartManager
//    EventPublisher eventPublisher
//    UpdateCartManager updateCartManager
//    CartDataProvider cartDataProvider
//    String guestId = "1234"
//
//    def setup() {
//        cartManager = Mock(CartManager)
//        eventPublisher = Mock(EventPublisher)
//        updateCartManager = new UpdateCartManager(cartManager, eventPublisher)
//        defaultListManager = new DefaultListManager(cartManager, updateCartManager, 3, false)
//        cartDataProvider = new CartDataProvider()
//    }
//
//    def "Test processDefaultListInd() while creating a list with default list as true and no preexisting lists present"() {
//        given:
//        List<CartResponse> cartResponseList = []
//
//        when:
//        def actual = defaultListManager.processDefaultListInd(guestId, true, null).block()
//
//        then:
//        1 * cartManager.getAllCarts(guestId, _) >> Mono.just(cartResponseList)
//        0 * cartManager.updateCart(_, _, _)
//
//        actual
//    }
//
//    def "Test processDefaultListInd() while creating a list with default list as false and no preexisting lists present"() {
//        given:
//        List<CartResponse> cartResponseList = []
//
//        when:
//        def actual = defaultListManager.processDefaultListInd(guestId, false, null).block()
//
//        then:
//        1 * cartManager.getAllCarts(guestId, _) >> Mono.just(cartResponseList)
//        0 * cartManager.updateCart(_, _, _)
//
//        actual
//    }
//
//    def "Test processDefaultListInd() while creating a list with default list as false and one non default preexisting list"() {
//        given:
//        UUID cartId = Uuids.timeBased()
//
//        ListMetaDataTO listMetaData = new ListMetaDataTO(false, LIST_STATUS.PENDING)
//        def metadata =  cartDataProvider.getMetaData(listMetaData, new UserMetaDataTO())
//
//        CartResponse cartResponse = cartDataProvider.getCartResponse(cartId, guestId, metadata)
//        List<CartResponse> cartResponseList = [cartResponse]
//
//        when:
//        def actual = defaultListManager.processDefaultListInd(guestId, false, null).block()
//
//        then:
//        1 * cartManager.getAllCarts(guestId, _) >> Mono.just(cartResponseList)
//        0 * cartManager.updateCart(_,_)
//
//        actual
//    }
//
//    def "Test processDefaultListInd() while creating a list with default list as true and one non default preexisting list"() {
//        given:
//        UUID cartId = Uuids.timeBased()
//
//        ListMetaDataTO listMetaData = new ListMetaDataTO(false, LIST_STATUS.PENDING)
//        def metadata =  cartDataProvider.getMetaData(listMetaData, new UserMetaDataTO())
//
//        CartResponse cartResponse = cartDataProvider.getCartResponse(cartId, guestId, metadata)
//        List<CartResponse> cartResponseList = [cartResponse]
//
//        when:
//        def actual = defaultListManager.processDefaultListInd(guestId, true, null).block()
//
//        then:
//        1 * cartManager.getAllCarts(guestId, _) >> Mono.just(cartResponseList)
//        0 * cartManager.updateCart(_,_)
//
//        actual
//    }
//
//    def "Test processDefaultListInd() while creating a list with default list as false and one default preexisting list"() {
//        given:
//        UUID cartId = Uuids.timeBased()
//
//        ListMetaDataTO listMetaData = new ListMetaDataTO(true, LIST_STATUS.PENDING)
//        def metadata =  cartDataProvider.getMetaData(listMetaData, new UserMetaDataTO())
//
//        CartResponse cartResponse = cartDataProvider.getCartResponse(cartId, guestId, metadata)
//        List<CartResponse> cartResponseList = [cartResponse]
//
//        when:
//        def actual = defaultListManager.processDefaultListInd(guestId, false, null).block()
//
//        then:
//        1 * cartManager.getAllCarts(guestId, _) >> Mono.just(cartResponseList)
//        0 * cartManager.updateCart(_,_)
//
//        !actual
//    }
//
//    def "Test processDefaultListInd() while creating a list with default list as true and one default preexisting list"() {
//        given:
//        UUID cartId = Uuids.timeBased()
//        String cartNumber = "testing"
//
//        ListMetaDataTO listMetaData1 = new ListMetaDataTO(true, LIST_STATUS.PENDING)
//        def metadata1 =  cartDataProvider.getMetaData(listMetaData1, new UserMetaDataTO())
//
//        CartResponse cartResponse1 = cartDataProvider.getCartResponse(cartId, guestId, metadata1)
//        List<CartResponse> cartResponseList = [cartResponse1]
//
//        ListMetaDataTO listMetaData2 = new ListMetaDataTO(false, LIST_STATUS.PENDING)
//        def metadata2 =  cartDataProvider.getMetaData(listMetaData2, new UserMetaDataTO())
//
//        CartResponse cartResponse2 = cartDataProvider.getCartResponse(cartId, guestId, "SHOPPING", cartNumber, metadata2)
//
//        when:
//        def actual = defaultListManager.processDefaultListInd(guestId, true, null).block()
//
//        then:
//        1 * cartManager.getAllCarts(guestId, _) >> Mono.just(cartResponseList)
//        1 * cartManager.updateCart(cartId, _ as CartPutRequest) >> { arguments ->
//            final CartPutRequest request = arguments[1]
//            def listMetaData = cartDataProvider.getListMetaDataFromCart(request.metadata as Map)
//            assert !listMetaData.defaultList
//            Mono.just(cartResponse2)
//        }
//        1 * eventPublisher.publishEvent(UpdateListNotifyEvent.eventType, _, _) >> Mono.just(GroovyMock(RecordMetadata))
//
//
//        actual
//    }
//
//    def "Test processDefaultListInd() while updating a list with default list as true and one default preexisting list"() {
//        given:
//        UUID cartId = Uuids.timeBased()
//        UUID updatedCartId = Uuids.timeBased()
//        String cartNumber = "testing"
//
//        ListMetaDataTO listMetaData1 = new ListMetaDataTO(true, LIST_STATUS.PENDING)
//        def metadata1 =  cartDataProvider.getMetaData(listMetaData1, new UserMetaDataTO())
//
//        CartResponse cartResponse1 = cartDataProvider.getCartResponse(cartId, guestId, metadata1)
//        List<CartResponse> cartResponseList = [cartResponse1]
//
//        ListMetaDataTO listMetaData2 = new ListMetaDataTO(false, LIST_STATUS.PENDING)
//        def metadata2 =  cartDataProvider.getMetaData(listMetaData2, new UserMetaDataTO())
//
//        CartResponse cartResponse2 = cartDataProvider.getCartResponse(cartId, guestId, "SHOPPING", cartNumber, metadata2)
//
//        def cartContentResponse1 = cartDataProvider.getCartContentsResponse(cartResponse1, [])
//
//        when:
//        def actual = defaultListManager.processDefaultListInd(guestId, true, updatedCartId).block()
//
//        then:
//        1 * cartManager.getListCartContents(_,false) >> Mono.just(cartContentResponse1)
//        1 * cartManager.getAllCarts(guestId, _) >> Mono.just(cartResponseList)
//        1 * cartManager.updateCart(cartId, _ as CartPutRequest) >> { arguments ->
//            final CartPutRequest request = arguments[1]
//            def listMetaData = cartDataProvider.getListMetaDataFromCart(request.metadata as Map)
//            assert !listMetaData.defaultList
//            Mono.just(cartResponse2)
//        }
//        1 * eventPublisher.publishEvent(UpdateListNotifyEvent.eventType, _, _) >> Mono.just(GroovyMock(RecordMetadata))
//
//        actual
//    }
//
//    def "Test processDefaultListInd() while updating a list with default list as true and it being the only list present"() {
//        given:
//        UUID cartId = Uuids.timeBased()
//
//        ListMetaDataTO listMetaData = new ListMetaDataTO(true, LIST_STATUS.PENDING)
//        def metadata =  cartDataProvider.getMetaData(listMetaData, new UserMetaDataTO())
//
//        CartResponse cartResponse = cartDataProvider.getCartResponse(cartId, guestId, metadata)
//        List<CartResponse> cartResponseList = [cartResponse]
//
//        def cartContentResponse = cartDataProvider.getCartContentsResponse(cartResponse, [])
//
//        when:
//        def actual = defaultListManager.processDefaultListInd(guestId, true, cartId).block()
//
//        then:
//        1 * cartManager.getListCartContents(_, false) >> Mono.just(cartContentResponse)
//        1 * cartManager.getAllCarts(guestId, _) >> Mono.just(cartResponseList)
//        0 * cartManager.updateCart(_,_)
//
//        actual
//    }
//
//    def "Test processDefaultListInd() while updating a list with default list as true and one non default preexisting list"() {
//        given:
//        UUID cartId = Uuids.timeBased()
//
//        ListMetaDataTO listMetaData1 = new ListMetaDataTO(false, LIST_STATUS.PENDING)
//        def metadata1 =  cartDataProvider.getMetaData(listMetaData1, new UserMetaDataTO())
//
//        CartResponse cartResponse1 = cartDataProvider.getCartResponse(cartId, guestId, metadata1)
//        List<CartResponse> cartResponseList = [cartResponse1]
//
//        def cartContentResponse = cartDataProvider.getCartContentsResponse(cartResponse1, [])
//
//        when:
//        def actual = defaultListManager.processDefaultListInd(guestId, true, cartId).block()
//
//        then:
//        1 * cartManager.getListCartContents(_, false) >> Mono.just(cartContentResponse)
//        1 * cartManager.getAllCarts(guestId, _) >> Mono.just(cartResponseList)
//        0 * cartManager.updateCart(_,_)
//
//        actual
//    }
//
//    def "Test processDefaultListInd() while a creating lists exceeding the max lists count"() {
//        given:
//        ListMetaDataTO listMetaData = new ListMetaDataTO(false, LIST_STATUS.PENDING)
//        def metadata =  cartDataProvider.getMetaData(listMetaData, new UserMetaDataTO())
//
//        CartResponse cartResponse1 = cartDataProvider.getCartResponse(Uuids.timeBased(), guestId, metadata)
//        CartResponse cartResponse2 = cartDataProvider.getCartResponse(Uuids.timeBased(), guestId, metadata)
//        CartResponse cartResponse3 = cartDataProvider.getCartResponse(Uuids.timeBased(), guestId, metadata)
//        List<CartResponse> cartResponseList = [cartResponse1, cartResponse2, cartResponse3]
//
//        when:
//        defaultListManager.processDefaultListInd(guestId, false, null).block()
//
//        then:
//        1 * cartManager.getAllCarts(guestId, _) >> Mono.just(cartResponseList)
//
//        thrown(BadRequestException)
//
//    }
//}
