package com.tgt.lists.atlas.api.async


import com.tgt.lists.cart.transport.*
import com.tgt.lists.cart.types.CartContentsFieldGroups
import com.tgt.lists.atlas.api.domain.CartManager
import com.tgt.lists.atlas.api.transport.ListMetaDataTO
import com.tgt.lists.atlas.api.transport.UserMetaDataTO
import com.tgt.lists.atlas.api.util.LIST_STATUS
import com.tgt.lists.atlas.util.CartDataProvider
import com.tgt.lists.atlas.util.TestListChannel
import com.tgt.lists.atlas.util.TestUtilConstants
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.LocalDateTime

class AbandonCartDurationServiceTest extends Specification {

    AbandonCartDurationService abandonCartDurationService
    CartManager cartManager
    CartDataProvider cartDataProvider
    String guestId = "1234"

    def setup() {
        cartManager = Mock(CartManager)
        abandonCartDurationService = new AbandonCartDurationService(cartManager, 2, true)
        cartDataProvider = new CartDataProvider()
    }

    def "Test setAbandonCartDuration() integration - with duration update time"() {
        given:
        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()

        ListMetaDataTO listMetaData1 = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        def metadata1 =  cartDataProvider.getMetaData(listMetaData1, new UserMetaDataTO())
        CartResponse pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId, metadata1)
        def pendingCartContentResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, null)

        def metadata2 = [(TestUtilConstants.DEFAULT_LIST_IND): true]
        CartResponse completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId,
                TestListChannel.WEB.toString(), CartType.LIST, "My list", "completed list", null, metadata2)

        when:
        def actual = abandonCartDurationService.setAbandonCartDuration(guestId, listId, LocalDateTime.now()).block()

        then:
        actual

        1 * cartManager.getListCartContents(listId, false) >> Mono.just(pendingCartContentResponse)
        1 * cartManager.updateCart(listId, _ as CartPutRequest) >> { arguments ->
            final CartPutRequest request = arguments[1]
            assert request.abandonAfterDuration.value == 3
            assert request.abandonAfterDuration.type == AbandonAfterDuration.Type.DAYS
            Mono.just(pendingCartResponse)
        }
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse)
        1 * cartManager.updateCart(completedListId, _ as CartPutRequest) >> { arguments ->
            final CartPutRequest request = arguments[1]
            assert request.abandonAfterDuration.value == 3
            assert request.abandonAfterDuration.type == AbandonAfterDuration.Type.DAYS
            Mono.just(completedCartResponse)
        }
    }

    def "Test setAbandonCartDuration() when the  updated abandonCartDuration value is same as the existing abandonCartDuration value"() {
        given:
        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def fieldGroups = new CartContentsFieldGroups([CartContentsFieldGroup.CART])

        ListMetaDataTO listMetaData1 = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        def metadata1 =  cartDataProvider.getMetaData(listMetaData1, new UserMetaDataTO())
        def abandonAfterDuration = new AbandonAfterDuration(BigDecimal.valueOf(3), AbandonAfterDuration.Type.DAYS)
        CartResponse pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId, metadata1, abandonAfterDuration)
        def pendingCartContentResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, null)

        when:
        def actual = abandonCartDurationService.setAbandonCartDuration(guestId, listId, LocalDateTime.now()).block()

        then:
        actual

        1 * cartManager.getListCartContents(listId, false) >> Mono.just(pendingCartContentResponse)
        0 * cartManager.updateCart(listId, _ as CartPutRequest)
        0 * cartManager.updateCart(completedListId, _ as CartPutRequest)
    }

    def "Test setAbandonCartDuration() when getting pending cart fails"() {
        given:
        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def fieldGroups = new CartContentsFieldGroups([CartContentsFieldGroup.CART])

        when:
        def actual = abandonCartDurationService.setAbandonCartDuration(guestId, listId, LocalDateTime.now()).block()

        then:
        !actual

        1 * cartManager.getListCartContents(listId, false) >>  Mono.error(new RuntimeException("some exception"))
        0 * cartManager.updateCart(listId, _ as CartPutRequest)
        0 * cartManager.updateCart(completedListId, _ as CartPutRequest)
    }

    def "Test setAbandonCartDuration() when updating pending cart fails"() {
        given:
        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def fieldGroups = new CartContentsFieldGroups([CartContentsFieldGroup.CART])

        ListMetaDataTO listMetaData1 = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        def metadata1 =  cartDataProvider.getMetaData(listMetaData1, new UserMetaDataTO())
        CartResponse pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId, metadata1)
        def pendingCartContentResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, null)

        def metadata2 = [(TestUtilConstants.DEFAULT_LIST_IND): true]
        CartResponse completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId,
                TestListChannel.WEB.toString(), CartType.LIST, "My list", "completed list", null, metadata2)

        when:
        def actual = abandonCartDurationService.setAbandonCartDuration(guestId, listId, LocalDateTime.now()).block()

        then:
        !actual

        1 * cartManager.getListCartContents(listId, false) >> Mono.just(pendingCartContentResponse)
        1 * cartManager.updateCart(listId, _ as CartPutRequest) >> { arguments ->
            final CartPutRequest request = arguments[1]
            assert request.abandonAfterDuration.value == 3
            assert request.abandonAfterDuration.type == AbandonAfterDuration.Type.DAYS
            Mono.error(new RuntimeException("some exception"))
        }
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse)
        1 * cartManager.updateCart(completedListId, _ as CartPutRequest) >> { arguments ->
            final CartPutRequest request = arguments[1]
            assert request.abandonAfterDuration.value == 3
            assert request.abandonAfterDuration.type == AbandonAfterDuration.Type.DAYS
            Mono.just(completedCartResponse)
        }
    }

    def "Test setAbandonCartDuration() when getting completed cart fails"() {
        given:
        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def fieldGroups = new CartContentsFieldGroups([CartContentsFieldGroup.CART])

        ListMetaDataTO listMetaData1 = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        def metadata1 =  cartDataProvider.getMetaData(listMetaData1, new UserMetaDataTO())
        CartResponse pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId, metadata1)
        def pendingCartContentResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, null)

        when:
        def actual = abandonCartDurationService.setAbandonCartDuration(guestId, listId, LocalDateTime.now()).block()

        then:
        !actual

        1 * cartManager.getListCartContents(listId, false) >> Mono.just(pendingCartContentResponse)
        1 * cartManager.updateCart(listId, _ as CartPutRequest) >> { arguments ->
            final CartPutRequest request = arguments[1]
            assert request.abandonAfterDuration.value == 3
            assert request.abandonAfterDuration.type == AbandonAfterDuration.Type.DAYS
            Mono.just(pendingCartResponse)
        }
        1 * cartManager.getCompletedListCart(listId) >> Mono.error(new RuntimeException("some exception"))
        0 * cartManager.updateCart(completedListId, _ as CartPutRequest)
    }

    def "Test setAbandonCartDuration() when updating completed cart fails"() {
        given:
        def listId = UUID.randomUUID()
        def completedListId = UUID.randomUUID()
        def fieldGroups = new CartContentsFieldGroups([CartContentsFieldGroup.CART])

        ListMetaDataTO listMetaData1 = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        def metadata1 =  cartDataProvider.getMetaData(listMetaData1, new UserMetaDataTO())
        CartResponse pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId, metadata1)
        def pendingCartContentResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, null)

        def metadata2 = [(TestUtilConstants.DEFAULT_LIST_IND): true]
        CartResponse completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId,
                TestListChannel.WEB.toString(), CartType.LIST, "My list", "completed list", null, metadata2)

        when:
        def actual = abandonCartDurationService.setAbandonCartDuration(guestId, listId, LocalDateTime.now()).block()

        then:
        !actual

        1 * cartManager.getListCartContents(listId, false) >> Mono.just(pendingCartContentResponse)
        1 * cartManager.updateCart(listId, _ as CartPutRequest) >> { arguments ->
            final CartPutRequest request = arguments[1]
            assert request.abandonAfterDuration.value == 3
            assert request.abandonAfterDuration.type == AbandonAfterDuration.Type.DAYS
            Mono.just(pendingCartResponse)
        }
        1 * cartManager.getCompletedListCart(listId) >> Mono.just(completedCartResponse)
        1 * cartManager.updateCart(completedListId, _ as CartPutRequest) >> { arguments ->
            final CartPutRequest request = arguments[1]
            assert request.abandonAfterDuration.value == 3
            assert request.abandonAfterDuration.type == AbandonAfterDuration.Type.DAYS
            Mono.error(new RuntimeException("some exception"))
        }
    }
}
