package com.tgt.lists.atlas.api.service

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.common.components.exception.BadRequestException
import com.tgt.lists.atlas.api.domain.CartManager
import com.tgt.lists.atlas.api.domain.EventPublisher
import com.tgt.lists.atlas.api.transport.EditListSortOrderRequestTO
import com.tgt.lists.atlas.api.util.Direction
import com.tgt.lists.atlas.kafka.model.EditListSortOrderActionEvent
import com.tgt.lists.atlas.util.CartDataProvider
import com.tgt.lists.atlas.util.TestListChannel
import com.tgt.lists.atlas.util.TestUtilConstants
import org.apache.kafka.clients.producer.RecordMetadata
import reactor.core.publisher.Mono
import spock.lang.Specification

class EditListSortOrderServiceTest extends Specification {

    EditListSortOrderService editListSortOrderService
    CartManager cartManager
    CartDataProvider cartDataProvider
    EventPublisher eventPublisher
    String guestId = "1234"

    def setup() {
        cartDataProvider = new CartDataProvider()
        cartManager = Mock(CartManager)
        eventPublisher = Mock(EventPublisher)
        editListSortOrderService = new EditListSortOrderService(eventPublisher, cartManager)
    }

    def "Test editListPosition() when primary and secondary list id are same"() {
        given:
        UUID listId = Uuids.timeBased()
        def editSortOrderRequest = new EditListSortOrderRequestTO(listId, listId, Direction.BELOW)

        def cartResponse1 = cartDataProvider.getCartResponse(listId, "1234",
                TestListChannel.MOBILE.toString(), CartType.LIST, "Pending list", "My pending list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])

        def cartResponse2 = cartDataProvider.getCartResponse(listId, "1234",
                TestListChannel.MOBILE.toString(), CartType.LIST, "Pending list", "My pending list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])

        def cartContentResponse1 = cartDataProvider.getCartContentsResponse(cartResponse1, [])

        when:
        def actual = editListSortOrderService.editListPosition(guestId, editSortOrderRequest).block()

        then:
        1 * cartManager.getListCartContents(listId, false) >> Mono.just(cartContentResponse1)
        1 * cartManager.getAllPendingCarts(_) >> Mono.just([cartResponse1, cartResponse2])

        actual
    }

    def "Test editListPosition() when primary and secondary list id are different"() {
        given:
        UUID listId = Uuids.timeBased()
        UUID listId1 = Uuids.timeBased()
        def editSortOrderRequest = new EditListSortOrderRequestTO(listId, listId1, Direction.BELOW)

        def cartResponse1 = cartDataProvider.getCartResponse(listId, "1234",
                TestListChannel.MOBILE.toString(), CartType.LIST, "Pending list", "My pending list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])

        def cartResponse2 = cartDataProvider.getCartResponse(listId1, "1234",
                TestListChannel.MOBILE.toString(), CartType.LIST, "Pending list", "My pending list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])

        def cartContentResponse1 = cartDataProvider.getCartContentsResponse(cartResponse1, [])

        when:
        def actual = editListSortOrderService.editListPosition(guestId, editSortOrderRequest).block()

        then:
        1 * cartManager.getListCartContents(listId, false) >> Mono.just(cartContentResponse1)
        1 * eventPublisher.publishEvent(EditListSortOrderActionEvent.eventType, _,listId.toString()) >> Mono.just(GroovyMock(RecordMetadata))
        1 * cartManager.getAllPendingCarts(_) >> Mono.just([cartResponse1, cartResponse2])

        actual
    }

    def "test editListPosition() when unauthorized list ids are passed"() {

        given:
        UUID primaryListId = Uuids.timeBased()
        UUID secondaryListId = Uuids.timeBased()
        Direction direction = Direction.ABOVE

        EditListSortOrderRequestTO editListSortOrderRequestTO = new EditListSortOrderRequestTO(primaryListId, secondaryListId, direction)
        def cartResponse1 = cartDataProvider.getCartResponse(Uuids.timeBased(), "1234",
                TestListChannel.MOBILE.toString(), CartType.LIST, "Pending list", "My pending list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])

        def cartResponse2 = cartDataProvider.getCartResponse(Uuids.timeBased(), "1234",
                TestListChannel.MOBILE.toString(), CartType.LIST, "Pending list", "My pending list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])

        def cartContentResponse1 = cartDataProvider.getCartContentsResponse(cartResponse1, [])

        when:
        editListSortOrderService.editListPosition("1234", editListSortOrderRequestTO).block()

        then:
        1 * cartManager.getListCartContents(primaryListId, false) >> Mono.just(cartContentResponse1)
        1 * cartManager.getAllPendingCarts(_) >> Mono.just([cartResponse1, cartResponse2])

        thrown BadRequestException
    }
}
