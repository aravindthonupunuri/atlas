package com.tgt.lists.atlas.api.service


import com.tgt.lists.cart.transport.CartContentsResponse
import com.tgt.lists.cart.transport.CartResponse
import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.atlas.api.domain.CartManager
import com.tgt.lists.atlas.api.domain.ContextContainerManager
import com.tgt.lists.atlas.api.domain.GuestPreferenceSortOrderManager
import com.tgt.lists.atlas.api.domain.model.GuestPreference
import com.tgt.lists.atlas.api.persistence.GuestPreferenceRepository
import com.tgt.lists.atlas.api.service.transform.list.ListsTransformationPipeline
import com.tgt.lists.atlas.api.service.transform.list.ListsTransformationPipelineConfiguration
import com.tgt.lists.atlas.api.service.transform.list.PopulateListItemsTransformationStep
import com.tgt.lists.atlas.api.service.transform.list.SortListsTransformationStep
import com.tgt.lists.atlas.api.transport.ListGetAllResponseTO
import com.tgt.lists.atlas.api.transport.ListMetaDataTO
import com.tgt.lists.atlas.api.transport.UserMetaDataTO
import com.tgt.lists.atlas.api.util.LIST_STATUS
import com.tgt.lists.atlas.api.util.ListSortFieldGroup
import com.tgt.lists.atlas.api.util.ListSortOrderGroup
import com.tgt.lists.atlas.util.CartDataProvider
import com.tgt.lists.atlas.util.TestListChannel
import reactor.core.publisher.Mono
import spock.lang.Specification

class GetAllListServiceTest extends Specification {

    GetAllListService getListsService
    GuestPreferenceSortOrderManager guestPreferenceSortOrderManager
    GuestPreferenceRepository guestPreferenceRepository
    CartManager cartManager
    ContextContainerManager contextContainerManager
    CartDataProvider cartDataProvider
    ListsTransformationPipelineConfiguration transformationPipelineConfiguration
    String guestId = "1234"

    def setup() {
        cartManager = Mock(CartManager)
        guestPreferenceRepository = Mock(GuestPreferenceRepository)
        guestPreferenceSortOrderManager = new GuestPreferenceSortOrderManager(guestPreferenceRepository)
        contextContainerManager = new ContextContainerManager()
        transformationPipelineConfiguration = new ListsTransformationPipelineConfiguration(cartManager, contextContainerManager, guestPreferenceSortOrderManager, true)
        getListsService = new GetAllListService(cartManager, transformationPipelineConfiguration, "SHOPPING", 10, true)
        cartDataProvider = new CartDataProvider()
    }

    def "test getAllListsForUser when the cart is empty"() {
        given:
        UUID cartId1 = UUID.randomUUID()
        UUID completedCartId1 = UUID.randomUUID()

        ListMetaDataTO metadata1 = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        CartResponse cartResponse1 = cartDataProvider.getCartResponse(cartId1, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list1", "1st list", null, cartDataProvider.getMetaData(metadata1, new UserMetaDataTO()))

        ListMetaDataTO completedMetadata1 = new ListMetaDataTO(true, LIST_STATUS.COMPLETED)
        CartResponse completedCartResponse1 = cartDataProvider.getCartResponse(completedCartId1,
            guestId, cartId1.toString(), cartDataProvider.getMetaData(completedMetadata1, new UserMetaDataTO()))

        List<CartResponse> cartResponseList = [cartResponse1, completedCartResponse1]

        CartContentsResponse Cart1ContentsResponse = cartDataProvider.getCartContentsResponse(cartId1, 0)

        CartContentsResponse completedCart1ContentsResponse = cartDataProvider.getCartContentsResponse(completedCartId1, 0)

        ListsTransformationPipeline listsTransformationPipeline = new ListsTransformationPipeline()
        listsTransformationPipeline.addStep(new PopulateListItemsTransformationStep()).addStep(new SortListsTransformationStep(ListSortFieldGroup.ADDED_DATE, ListSortOrderGroup.ASCENDING))

        when:
        List<ListGetAllResponseTO> listWithMetadata = getListsService.getAllListsForUser(
                guestId, listsTransformationPipeline).block()

        then:
        1 * cartManager.getAllCarts(guestId, _) >> Mono.just(cartResponseList)
        1 * cartManager.getListCartContents({ cid -> cid == cartId1}, true) >> Mono.just(Cart1ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == completedCartId1}, true) >> Mono.just(completedCart1ContentsResponse)
        0 * guestPreferenceRepository.find(guestId)

        listWithMetadata.size() == 1
        listWithMetadata[0].totalItemsCount == 0
        listWithMetadata[0].pendingItemsCount == 0
        listWithMetadata[0].completedItemsCount == 0
    }

    def "test getAllListsForUser fails while getting completed contents"() {
        given:
        UUID cartId1 = UUID.randomUUID()
        UUID completedCartId1 = UUID.randomUUID()

        ListMetaDataTO metadata1 = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        CartResponse cartResponse1 = cartDataProvider.getCartResponse(cartId1, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list1", "1st list", null, cartDataProvider.getMetaData(metadata1, new UserMetaDataTO()))

        ListMetaDataTO completedMetadata1 = new ListMetaDataTO(true, LIST_STATUS.COMPLETED)
        CartResponse completedCartResponse1 = cartDataProvider.getCartResponse(completedCartId1,
            guestId, cartId1.toString(), cartDataProvider.getMetaData(completedMetadata1, new UserMetaDataTO()))

        List<CartResponse> cartResponseList = [cartResponse1, completedCartResponse1]

        CartContentsResponse Cart1ContentsResponse = cartDataProvider.getCartContentsResponse(cartId1, 0)

        ListsTransformationPipeline listsTransformationPipeline = new ListsTransformationPipeline()
        listsTransformationPipeline.addStep(new PopulateListItemsTransformationStep()).addStep(new SortListsTransformationStep(ListSortFieldGroup.ADDED_DATE, ListSortOrderGroup.ASCENDING))

        when:
        List<ListGetAllResponseTO> listWithMetadata = getListsService.getAllListsForUser(
                guestId, listsTransformationPipeline).block()

        then:
        1 * cartManager.getAllCarts(guestId, _) >> Mono.just(cartResponseList)
        1 * cartManager.getListCartContents({ cid -> cid == cartId1}, true) >> Mono.just(Cart1ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == completedCartId1}, true) >> Mono.error(new RuntimeException("some exception"))
        0 * guestPreferenceRepository.find(guestId)

        listWithMetadata.size() == 1
        listWithMetadata[0].totalItemsCount == 0
        listWithMetadata[0].pendingItemsCount == 0
        listWithMetadata[0].completedItemsCount == 0
    }

    def "test getAllListsForUser fails while getting pending contents"() {
        given:
        UUID cartId1 = UUID.randomUUID()
        UUID completedCartId1 = UUID.randomUUID()

        ListMetaDataTO metadata1 = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        CartResponse cartResponse1 = cartDataProvider.getCartResponse(cartId1, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list1", "1st list", null, cartDataProvider.getMetaData(metadata1, new UserMetaDataTO()))

        ListMetaDataTO completedMetadata1 = new ListMetaDataTO(true, LIST_STATUS.COMPLETED)
        CartResponse completedCartResponse1 = cartDataProvider.getCartResponse(completedCartId1,
            guestId, cartId1.toString(), cartDataProvider.getMetaData(completedMetadata1, new UserMetaDataTO()))

        List<CartResponse> cartResponseList = [cartResponse1, completedCartResponse1]
        CartContentsResponse completedCart1ContentsResponse = cartDataProvider.getCartContentsResponse(completedCartId1, 0)

        ListsTransformationPipeline listsTransformationPipeline = new ListsTransformationPipeline()
        listsTransformationPipeline.addStep(new PopulateListItemsTransformationStep()).addStep(new SortListsTransformationStep(ListSortFieldGroup.ADDED_DATE, ListSortOrderGroup.ASCENDING))

        when:
        List<ListGetAllResponseTO> listWithMetadata = getListsService.getAllListsForUser(
            guestId, listsTransformationPipeline).block()

        then:
        1 * cartManager.getAllCarts(guestId, _) >> Mono.just(cartResponseList)
        1 * cartManager.getListCartContents({ cid -> cid == cartId1},true) >> Mono.error(new RuntimeException("some exception"))
        1 * cartManager.getListCartContents({ cid -> cid == completedCartId1}, true) >> Mono.just(completedCart1ContentsResponse)
        0 * guestPreferenceRepository.find(guestId)

        listWithMetadata.size() == 1
        listWithMetadata[0].totalItemsCount == 0
        listWithMetadata[0].pendingItemsCount == 0
        listWithMetadata[0].completedItemsCount == 0
    }

    def "test getAllListsForUser fails while getting cart contents"() {
        given:
        UUID cartId1 = UUID.randomUUID()
        UUID completedCartId1 = UUID.randomUUID()

        ListMetaDataTO metadata1 = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        CartResponse cartResponse1 = cartDataProvider.getCartResponse(cartId1, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list1", "1st list", null, cartDataProvider.getMetaData(metadata1, new UserMetaDataTO()))

        ListMetaDataTO completedMetadata1 = new ListMetaDataTO(true, LIST_STATUS.COMPLETED)
        CartResponse completedCartResponse1 = cartDataProvider.getCartResponse(completedCartId1,
            guestId, cartId1.toString(), cartDataProvider.getMetaData(completedMetadata1, new UserMetaDataTO()))

        List<CartResponse> cartResponseList = [cartResponse1, completedCartResponse1]

        ListsTransformationPipeline listsTransformationPipeline = new ListsTransformationPipeline()
        listsTransformationPipeline.addStep(new PopulateListItemsTransformationStep()).addStep(new SortListsTransformationStep(ListSortFieldGroup.ADDED_DATE, ListSortOrderGroup.ASCENDING))

        when:
        List<ListGetAllResponseTO> listWithMetadata = getListsService.getAllListsForUser(
            guestId, listsTransformationPipeline).block()

        then:
        1 * cartManager.getAllCarts(guestId, _) >> Mono.just(cartResponseList)
        1 * cartManager.getListCartContents({ cid -> cid == cartId1}, true) >> Mono.error(new RuntimeException("some exception"))
        1 * cartManager.getListCartContents({ cid -> cid == completedCartId1}, true) >> Mono.error(new RuntimeException("some exception"))
        0 * guestPreferenceRepository.find(guestId)

        listWithMetadata.size() == 1
        listWithMetadata[0].totalItemsCount == 0
        listWithMetadata[0].pendingItemsCount == 0
        listWithMetadata[0].completedItemsCount == 0
    }

    def "test getAllListsForUserSortingByAddedDate"() {
        given:
        UUID cartId1 = UUID.randomUUID()
        UUID cartId2 = UUID.randomUUID()
        UUID cartId3 = UUID.randomUUID()
        UUID completedCartId1 = UUID.randomUUID()
        UUID completedCartId2 = UUID.randomUUID()
        UUID completedCartId3 = UUID.randomUUID()

        ListMetaDataTO metadata1 = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        CartResponse cartResponse1 = cartDataProvider.getCartResponse(cartId1, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list1", "1st list", null, cartDataProvider.getMetaData(metadata1, new UserMetaDataTO()))

        ListMetaDataTO completedMetadata1 = new ListMetaDataTO(true, LIST_STATUS.COMPLETED)
        CartResponse completedCartResponse1 = cartDataProvider.getCartResponse(completedCartId1,
            guestId, cartId1.toString(), cartDataProvider.getMetaData(completedMetadata1, new UserMetaDataTO()))

        ListMetaDataTO metadata2 = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse cartResponse2 = cartDataProvider.getCartResponse(cartId2, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list2", "2nd list", null, cartDataProvider.getMetaData(metadata2, new UserMetaDataTO()))

        ListMetaDataTO completedMetadata2 = new ListMetaDataTO(false, LIST_STATUS.COMPLETED)
        CartResponse completedCartResponse2 = cartDataProvider.getCartResponse(completedCartId2, guestId,
            cartId2.toString(), cartDataProvider.getMetaData(completedMetadata2, new UserMetaDataTO()))

        ListMetaDataTO metadata3 = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse cartResponse3 = cartDataProvider.getCartResponse(cartId3, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list3", "3rd list", null, cartDataProvider.getMetaData(metadata3, new UserMetaDataTO()))

        ListMetaDataTO completedMetadata3 = new ListMetaDataTO(false, LIST_STATUS.COMPLETED)
        CartResponse completedCartResponse3 = cartDataProvider.getCartResponse(completedCartId3, guestId,
            cartId3.toString(), cartDataProvider.getMetaData(completedMetadata3, new UserMetaDataTO()))

        List<CartResponse> cartResponseList = [cartResponse1, completedCartResponse1, cartResponse2, completedCartResponse2, cartResponse3, completedCartResponse3]

        CartContentsResponse Cart1ContentsResponse = cartDataProvider.getCartContentsResponse(cartId1, 1)

        CartContentsResponse Cart2ContentsResponse = cartDataProvider.getCartContentsResponse(cartId2, 2)

        CartContentsResponse Cart3ContentsResponse = cartDataProvider.getCartContentsResponse(cartId3, 3)

        CartContentsResponse completedCart1ContentsResponse = cartDataProvider.getCartContentsResponse(completedCartId1, 1)

        CartContentsResponse completedCart2ContentsResponse = cartDataProvider.getCartContentsResponse(completedCartId2, 2)

        CartContentsResponse completedCart3ContentsResponse = cartDataProvider.getCartContentsResponse(completedCartId3, 3)

        ListsTransformationPipeline listsTransformationPipeline = new ListsTransformationPipeline()
        listsTransformationPipeline.addStep(new PopulateListItemsTransformationStep()).addStep(new SortListsTransformationStep(ListSortFieldGroup.ADDED_DATE, ListSortOrderGroup.ASCENDING))

        when:
        List<ListGetAllResponseTO> listWithMetadata = getListsService.getAllListsForUser(
            guestId, listsTransformationPipeline).block()

        then:
        1 * cartManager.getAllCarts(guestId, _) >> Mono.just(cartResponseList)
        1 * cartManager.getListCartContents({ cid -> cid == cartId1}, true) >> Mono.just(Cart1ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == cartId2}, true) >> Mono.just(Cart2ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == cartId3}, true) >> Mono.just(Cart3ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == completedCartId1}, true) >> Mono.just(completedCart1ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == completedCartId2}, true) >> Mono.just(completedCart2ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == completedCartId3}, true) >> Mono.just(completedCart3ContentsResponse)
        0 * guestPreferenceRepository.find(guestId)

        listWithMetadata.size() == 3
        listWithMetadata[0].totalItemsCount == 2
        listWithMetadata[1].totalItemsCount == 4
        listWithMetadata[2].totalItemsCount == 6
    }

    def "test getAllListsForUserSortingByListTitle"() {
        given:
        UUID cartId1 = UUID.randomUUID()
        UUID cartId2 = UUID.randomUUID()
        UUID cartId3 = UUID.randomUUID()
        UUID completedCartId1 = UUID.randomUUID()
        UUID completedCartId2 = UUID.randomUUID()
        UUID completedCartId3 = UUID.randomUUID()

        ListMetaDataTO metadata1 = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        CartResponse cartResponse1 = cartDataProvider.getCartResponse(cartId1, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "Aa", "1st list", null, cartDataProvider.getMetaData(metadata1, new UserMetaDataTO()))

        ListMetaDataTO completedMetadata1 = new ListMetaDataTO(true, LIST_STATUS.COMPLETED)
        CartResponse completedCartResponse1 = cartDataProvider.getCartResponse(completedCartId1,
            guestId, cartId1.toString(), cartDataProvider.getMetaData(completedMetadata1, new UserMetaDataTO()))

        ListMetaDataTO metadata2 = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse cartResponse2 = cartDataProvider.getCartResponse(cartId2, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "ab", "2nd list", null, cartDataProvider.getMetaData(metadata2, new UserMetaDataTO()))

        ListMetaDataTO completedMetadata2 = new ListMetaDataTO(false, LIST_STATUS.COMPLETED)
        CartResponse completedCartResponse2 = cartDataProvider.getCartResponse(completedCartId2, guestId,
            cartId2.toString(), cartDataProvider.getMetaData(completedMetadata2, new UserMetaDataTO()))

        ListMetaDataTO metadata3 = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse cartResponse3 = cartDataProvider.getCartResponse(cartId3, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "Ba", "3rd list", null, cartDataProvider.getMetaData(metadata3, new UserMetaDataTO()))

        ListMetaDataTO completedMetadata3 = new ListMetaDataTO(false, LIST_STATUS.COMPLETED)
        CartResponse completedCartResponse3 = cartDataProvider.getCartResponse(completedCartId3, guestId,
            cartId3.toString(), cartDataProvider.getMetaData(completedMetadata3, new UserMetaDataTO()))

        List<CartResponse> cartResponseList = [cartResponse1, completedCartResponse1, cartResponse2, completedCartResponse2, cartResponse3, completedCartResponse3]

        CartContentsResponse Cart1ContentsResponse = cartDataProvider.getCartContentsResponse(cartId1, 1)

        CartContentsResponse Cart2ContentsResponse = cartDataProvider.getCartContentsResponse(cartId2, 2)

        CartContentsResponse Cart3ContentsResponse = cartDataProvider.getCartContentsResponse(cartId3, 3)

        CartContentsResponse completedCart1ContentsResponse = cartDataProvider.getCartContentsResponse(completedCartId1, 1)

        CartContentsResponse completedCart2ContentsResponse = cartDataProvider.getCartContentsResponse(completedCartId2, 2)

        CartContentsResponse completedCart3ContentsResponse = cartDataProvider.getCartContentsResponse(completedCartId3, 3)

        ListsTransformationPipeline listsTransformationPipeline = new ListsTransformationPipeline()
        listsTransformationPipeline.addStep(new PopulateListItemsTransformationStep()).addStep(new SortListsTransformationStep(ListSortFieldGroup.LIST_TITLE, ListSortOrderGroup.ASCENDING))

        when:
        List<ListGetAllResponseTO> listWithMetadata = getListsService.getAllListsForUser(
            guestId, listsTransformationPipeline).block()

        then:
        1 * cartManager.getAllCarts(guestId, _) >> Mono.just(cartResponseList)
        1 * cartManager.getListCartContents({ cid -> cid == cartId1}, true) >> Mono.just(Cart1ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == cartId2}, true) >> Mono.just(Cart2ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == cartId3}, true) >> Mono.just(Cart3ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == completedCartId1}, true) >> Mono.just(completedCart1ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == completedCartId2}, true) >> Mono.just(completedCart2ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == completedCartId3}, true) >> Mono.just(completedCart3ContentsResponse)
        0 * guestPreferenceRepository.find(guestId)

        listWithMetadata.size() == 3
        listWithMetadata[0].totalItemsCount == 2
        listWithMetadata[1].totalItemsCount == 4
        listWithMetadata[2].totalItemsCount == 6
    }

    def "test getAllListsForUser() when sorting ascending by list position"() {
        given:
        UUID cartId1 = UUID.randomUUID()
        UUID cartId2 = UUID.randomUUID()
        UUID cartId3 = UUID.randomUUID()
        UUID completedCartId1 = UUID.randomUUID()
        UUID completedCartId2 = UUID.randomUUID()
        UUID completedCartId3 = UUID.randomUUID()

        ListMetaDataTO metadata1 = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        CartResponse cartResponse1 = cartDataProvider.getCartResponse(cartId1, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list1", "1st list", null, cartDataProvider.getMetaData(metadata1, new UserMetaDataTO()))

        ListMetaDataTO completedMetadata1 = new ListMetaDataTO(true, LIST_STATUS.COMPLETED)
        CartResponse completedCartResponse1 = cartDataProvider.getCartResponse(completedCartId1,
            guestId, cartId1.toString(), cartDataProvider.getMetaData(completedMetadata1, new UserMetaDataTO()))

        ListMetaDataTO metadata2 = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse cartResponse2 = cartDataProvider.getCartResponse(cartId2, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list2", "2nd list", null, cartDataProvider.getMetaData(metadata2, new UserMetaDataTO()))

        ListMetaDataTO completedMetadata2 = new ListMetaDataTO(false, LIST_STATUS.COMPLETED)
        CartResponse completedCartResponse2 = cartDataProvider.getCartResponse(completedCartId2, guestId,
            cartId2.toString(), cartDataProvider.getMetaData(completedMetadata2, new UserMetaDataTO()))

        ListMetaDataTO metadata3 = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse cartResponse3 = cartDataProvider.getCartResponse(cartId3, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list3", "3rd list", null, cartDataProvider.getMetaData(metadata3, new UserMetaDataTO()))

        ListMetaDataTO completedMetadata3 = new ListMetaDataTO(false, LIST_STATUS.COMPLETED)
        CartResponse completedCartResponse3 = cartDataProvider.getCartResponse(completedCartId3, guestId,
            cartId3.toString(), cartDataProvider.getMetaData(completedMetadata3, new UserMetaDataTO()))

        List<CartResponse> cartResponseList = [cartResponse1, completedCartResponse1, cartResponse2, completedCartResponse2, cartResponse3, completedCartResponse3]

        CartContentsResponse Cart1ContentsResponse = cartDataProvider.getCartContentsResponse(cartId1, 1)

        CartContentsResponse Cart2ContentsResponse = cartDataProvider.getCartContentsResponse(cartId2, 2)

        CartContentsResponse Cart3ContentsResponse = cartDataProvider.getCartContentsResponse(cartId3, 3)

        CartContentsResponse completedCart1ContentsResponse = cartDataProvider.getCartContentsResponse(completedCartId1, 1)

        CartContentsResponse completedCart2ContentsResponse = cartDataProvider.getCartContentsResponse(completedCartId2, 2)

        CartContentsResponse completedCart3ContentsResponse = cartDataProvider.getCartContentsResponse(completedCartId3, 3)

        GuestPreference guestPreference = new GuestPreference(guestId, cartId3.toString()
            + "," + cartId2.toString() + "," + cartId1.toString(), null, null)

        ListsTransformationPipeline listsTransformationPipeline = new ListsTransformationPipeline()
        listsTransformationPipeline.addStep(new PopulateListItemsTransformationStep()).addStep(new SortListsTransformationStep(ListSortFieldGroup.LIST_POSITION, ListSortOrderGroup.ASCENDING))

        when:
        List<ListGetAllResponseTO> listWithMetadata = getListsService.getAllListsForUser(
            guestId, listsTransformationPipeline).block()

        then:
        1 * cartManager.getAllCarts(guestId, _) >> Mono.just(cartResponseList)
        1 * cartManager.getListCartContents({ cid -> cid == cartId1}, true) >> Mono.just(Cart1ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == cartId2}, true) >> Mono.just(Cart2ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == cartId3}, true) >> Mono.just(Cart3ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == completedCartId1}, true) >> Mono.just(completedCart1ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == completedCartId2}, true) >> Mono.just(completedCart2ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == completedCartId3}, true) >> Mono.just(completedCart3ContentsResponse)
        1 * guestPreferenceRepository.find(guestId) >> Mono.just(guestPreference)

        listWithMetadata.size() == 3
        listWithMetadata[0].listId == cartId3
        listWithMetadata[1].listId == cartId2
        listWithMetadata[2].listId == cartId1
        listWithMetadata[0].totalItemsCount == 6
        listWithMetadata[1].totalItemsCount == 4
        listWithMetadata[2].totalItemsCount == 2
    }

    def "test getAllListsForUser() when sorting descending by list position"() {
        given:
        UUID cartId1 = UUID.randomUUID()
        UUID cartId2 = UUID.randomUUID()
        UUID cartId3 = UUID.randomUUID()
        UUID completedCartId1 = UUID.randomUUID()
        UUID completedCartId2 = UUID.randomUUID()
        UUID completedCartId3 = UUID.randomUUID()

        ListMetaDataTO metadata1 = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        CartResponse cartResponse1 = cartDataProvider.getCartResponse(cartId1, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list1", "1st list", null, cartDataProvider.getMetaData(metadata1, new UserMetaDataTO()))

        ListMetaDataTO completedMetadata1 = new ListMetaDataTO(true, LIST_STATUS.COMPLETED)
        CartResponse completedCartResponse1 = cartDataProvider.getCartResponse(completedCartId1,
            guestId, cartId1.toString(), cartDataProvider.getMetaData(completedMetadata1, new UserMetaDataTO()))

        ListMetaDataTO metadata2 = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse cartResponse2 = cartDataProvider.getCartResponse(cartId2, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list2", "2nd list", null, cartDataProvider.getMetaData(metadata2, new UserMetaDataTO()))

        ListMetaDataTO completedMetadata2 = new ListMetaDataTO(false, LIST_STATUS.COMPLETED)
        CartResponse completedCartResponse2 = cartDataProvider.getCartResponse(completedCartId2, guestId,
            cartId2.toString(), cartDataProvider.getMetaData(completedMetadata2, new UserMetaDataTO()))

        ListMetaDataTO metadata3 = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse cartResponse3 = cartDataProvider.getCartResponse(cartId3, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list3", "3rd list", null, cartDataProvider.getMetaData(metadata3, new UserMetaDataTO()))

        ListMetaDataTO completedMetadata3 = new ListMetaDataTO(false, LIST_STATUS.COMPLETED)
        CartResponse completedCartResponse3 = cartDataProvider.getCartResponse(completedCartId3, guestId,
            cartId3.toString(), cartDataProvider.getMetaData(completedMetadata3, new UserMetaDataTO()))

        List<CartResponse> cartResponseList = [cartResponse1, completedCartResponse1, cartResponse2, completedCartResponse2, cartResponse3, completedCartResponse3]

        CartContentsResponse Cart1ContentsResponse = cartDataProvider.getCartContentsResponse(cartId1, 1)

        CartContentsResponse Cart2ContentsResponse = cartDataProvider.getCartContentsResponse(cartId2, 2)

        CartContentsResponse Cart3ContentsResponse = cartDataProvider.getCartContentsResponse(cartId3, 3)

        CartContentsResponse completedCart1ContentsResponse = cartDataProvider.getCartContentsResponse(completedCartId1, 1)

        CartContentsResponse completedCart2ContentsResponse = cartDataProvider.getCartContentsResponse(completedCartId2, 2)

        CartContentsResponse completedCart3ContentsResponse = cartDataProvider.getCartContentsResponse(completedCartId3, 3)

        GuestPreference guestPreference = new GuestPreference(guestId, cartId3.toString()
            + "," + cartId2.toString() + "," + cartId1.toString(), null, null)

        ListsTransformationPipeline listsTransformationPipeline = new ListsTransformationPipeline()
        listsTransformationPipeline.addStep(new PopulateListItemsTransformationStep()).addStep(new SortListsTransformationStep(ListSortFieldGroup.LIST_POSITION, ListSortOrderGroup.DESCENDING))

        when:
        List<ListGetAllResponseTO> listWithMetadata = getListsService.getAllListsForUser(
            guestId, listsTransformationPipeline).block()

        then:
        1 * cartManager.getAllCarts(guestId, _) >> Mono.just(cartResponseList)
        1 * cartManager.getListCartContents({ cid -> cid == cartId1}, true) >> Mono.just(Cart1ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == cartId2}, true) >> Mono.just(Cart2ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == cartId3}, true) >> Mono.just(Cart3ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == completedCartId1}, true) >> Mono.just(completedCart1ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == completedCartId2}, true) >> Mono.just(completedCart2ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == completedCartId3}, true) >> Mono.just(completedCart3ContentsResponse)
        1 * guestPreferenceRepository.find(guestId) >> Mono.just(guestPreference)

        listWithMetadata.size() == 3
        listWithMetadata[0].listId == cartId1
        listWithMetadata[1].listId == cartId2
        listWithMetadata[2].listId == cartId3
        listWithMetadata[0].totalItemsCount == 2
        listWithMetadata[1].totalItemsCount == 4
        listWithMetadata[2].totalItemsCount == 6
    }

    def "test getAllListsForUser() when sorting list position by desc not available for certain lists"() {
        given:
        UUID cartId1 = UUID.randomUUID()
        UUID cartId2 = UUID.randomUUID()
        UUID cartId3 = UUID.randomUUID()
        UUID completedCartId1 = UUID.randomUUID()
        UUID completedCartId2 = UUID.randomUUID()
        UUID completedCartId3 = UUID.randomUUID()

        ListMetaDataTO metadata1 = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        CartResponse cartResponse1 = cartDataProvider.getCartResponse(cartId1, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list1", "1st list", null, cartDataProvider.getMetaData(metadata1, new UserMetaDataTO()))

        ListMetaDataTO completedMetadata1 = new ListMetaDataTO(true, LIST_STATUS.COMPLETED)
        CartResponse completedCartResponse1 = cartDataProvider.getCartResponse(completedCartId1,
            guestId, cartId1.toString(), cartDataProvider.getMetaData(completedMetadata1, new UserMetaDataTO()))

        ListMetaDataTO metadata2 = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse cartResponse2 = cartDataProvider.getCartResponse(cartId2, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list2", "2nd list", null, cartDataProvider.getMetaData(metadata2, new UserMetaDataTO()))

        ListMetaDataTO completedMetadata2 = new ListMetaDataTO(false, LIST_STATUS.COMPLETED)
        CartResponse completedCartResponse2 = cartDataProvider.getCartResponse(completedCartId2, guestId,
            cartId2.toString(), cartDataProvider.getMetaData(completedMetadata2, new UserMetaDataTO()))

        ListMetaDataTO metadata3 = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse cartResponse3 = cartDataProvider.getCartResponse(cartId3, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list3", "3rd list", null, cartDataProvider.getMetaData(metadata3, new UserMetaDataTO()))

        ListMetaDataTO completedMetadata3 = new ListMetaDataTO(false, LIST_STATUS.COMPLETED)
        CartResponse completedCartResponse3 = cartDataProvider.getCartResponse(completedCartId3, guestId,
            cartId3.toString(), cartDataProvider.getMetaData(completedMetadata3, new UserMetaDataTO()))

        List<CartResponse> cartResponseList = [cartResponse1, completedCartResponse1, cartResponse2, completedCartResponse2, cartResponse3, completedCartResponse3]

        CartContentsResponse Cart1ContentsResponse = cartDataProvider.getCartContentsResponse(cartId1, 1)

        CartContentsResponse Cart2ContentsResponse = cartDataProvider.getCartContentsResponse(cartId2, 2)

        CartContentsResponse Cart3ContentsResponse = cartDataProvider.getCartContentsResponse(cartId3, 3)

        CartContentsResponse completedCart1ContentsResponse = cartDataProvider.getCartContentsResponse(completedCartId1, 1)

        CartContentsResponse completedCart2ContentsResponse = cartDataProvider.getCartContentsResponse(completedCartId2, 2)

        CartContentsResponse completedCart3ContentsResponse = cartDataProvider.getCartContentsResponse(completedCartId3, 3)

        GuestPreference guestPreference = new GuestPreference(guestId, cartId3.toString(), null, null)

        ListsTransformationPipeline listsTransformationPipeline = new ListsTransformationPipeline()
        listsTransformationPipeline.addStep(new PopulateListItemsTransformationStep()).addStep(new SortListsTransformationStep(ListSortFieldGroup.LIST_POSITION, ListSortOrderGroup.ASCENDING))

        when:
        List<ListGetAllResponseTO> listWithMetadata = getListsService.getAllListsForUser(
            guestId, listsTransformationPipeline).block()

        then:
        1 * cartManager.getAllCarts(guestId, _) >> Mono.just(cartResponseList)
        1 * cartManager.getListCartContents({ cid -> cid == cartId1}, true) >> Mono.just(Cart1ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == cartId2}, true) >> Mono.just(Cart2ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == cartId3}, true) >> Mono.just(Cart3ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == completedCartId1}, true) >> Mono.just(completedCart1ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == completedCartId2}, true) >> Mono.just(completedCart2ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == completedCartId3}, true) >> Mono.just(completedCart3ContentsResponse)
        1 * guestPreferenceRepository.find(guestId) >> Mono.just(guestPreference)

        listWithMetadata.size() == 3
        listWithMetadata[0].listId == cartId3
        listWithMetadata[1].listId == cartId1
        listWithMetadata[2].listId == cartId2
        listWithMetadata[0].totalItemsCount == 6
        listWithMetadata[1].totalItemsCount == 2
        listWithMetadata[2].totalItemsCount == 4
    }

    def "test getAllListsForUser() when no sorting list position available"() {
        given:
        UUID cartId1 = UUID.randomUUID()
        UUID cartId2 = UUID.randomUUID()
        UUID cartId3 = UUID.randomUUID()
        ListMetaDataTO metadata1 = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        CartResponse cartResponse1 = cartDataProvider.getCartResponse(cartId1, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list1", "1st list", null, cartDataProvider.getMetaData(metadata1, new UserMetaDataTO()))
        ListMetaDataTO metadata2 = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse cartResponse2 = cartDataProvider.getCartResponse(cartId2, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list2", "2nd list", null, cartDataProvider.getMetaData(metadata2, new UserMetaDataTO()))
        ListMetaDataTO metadata3 = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse cartResponse3 = cartDataProvider.getCartResponse(cartId3, guestId,
            TestListChannel.WEB.toString(), CartType.LIST, "My list3", "3rd list", null, cartDataProvider.getMetaData(metadata3, new UserMetaDataTO()))

        List<CartResponse> cartResponseList = [cartResponse1, cartResponse2, cartResponse3]

        CartContentsResponse Cart1ContentsResponse = cartDataProvider.getCartContentsResponse(cartId1, 1)

        CartContentsResponse Cart2ContentsResponse = cartDataProvider.getCartContentsResponse(cartId2, 2)

        CartContentsResponse Cart3ContentsResponse = cartDataProvider.getCartContentsResponse(cartId3, 3)

        ListsTransformationPipeline listsTransformationPipeline = new ListsTransformationPipeline()
        listsTransformationPipeline.addStep(new PopulateListItemsTransformationStep()).addStep(new SortListsTransformationStep(ListSortFieldGroup.LIST_POSITION, ListSortOrderGroup.ASCENDING))

        when:
        List<ListGetAllResponseTO> listWithMetadata = getListsService.getAllListsForUser(
            guestId, listsTransformationPipeline).block()

        then:
        1 * cartManager.getAllCarts(guestId, _) >> Mono.just(cartResponseList)
        1 * cartManager.getListCartContents({ cid -> cid == cartId1}, true) >> Mono.just(Cart1ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == cartId2}, true) >> Mono.just(Cart2ContentsResponse)
        1 * cartManager.getListCartContents({ cid -> cid == cartId3}, true) >> Mono.just(Cart3ContentsResponse)
        1 * guestPreferenceRepository.find(guestId) >> Mono.error(new RuntimeException("some exception"))

        listWithMetadata.size() == 3
        listWithMetadata[0].listId == cartId1
        listWithMetadata[1].listId == cartId2
        listWithMetadata[2].listId == cartId3
    }
}
