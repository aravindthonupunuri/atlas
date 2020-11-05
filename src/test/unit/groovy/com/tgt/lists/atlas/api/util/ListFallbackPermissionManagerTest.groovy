package com.tgt.lists.atlas.api.util

import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.util.ListDataProvider
import com.tgt.lists.common.components.exception.ForbiddenException
import com.tgt.lists.common.components.exception.ResourceNotFoundException
import io.micronaut.http.HttpMethod
import reactor.core.publisher.Mono
import spock.lang.Specification

class ListFallbackPermissionManagerTest extends Specification {

    ListFallbackPermissionManager listFallbackPermissionManager
    ListRepository listRepository
    ListDataProvider listDataProvider = new ListDataProvider()

    def setup() {
        listRepository = Mock(ListRepository)
        listFallbackPermissionManager = new ListFallbackPermissionManager(listRepository)
    }

    def "test authorize success"() {
        given:
        def userId = "1234"
        def listId = UUID.randomUUID()
        def method = HttpMethod.GET
        ListEntity listEntity = listDataProvider.createListEntity(listId, "test-list", "SHOPPING", "", userId, null)

        when:
        def result = listFallbackPermissionManager.authorize(userId, listId, method).block()

        then:
        result == true

        1 * listRepository.findListById(listId) >> Mono.just(listEntity)
    }

    def "test authorize denied with unauthorized user"() {
        given:
        def userId = "1234"
        def listId = UUID.randomUUID()
        def method = HttpMethod.GET

        ListEntity listEntity = listDataProvider.createListEntity(listId, "test-list", "SHOPPING", "", "1000", null)

        when:
        listFallbackPermissionManager.authorize(userId, listId, method).block()

        then:
        thrown(ForbiddenException)

        1 * listRepository.findListById(listId) >> Mono.just(listEntity)
    }

    def "test authorize denied with empty listRepository response"() {
        given:
        def userId = "1234"
        def listId = UUID.randomUUID()
        def method = HttpMethod.GET

        when:
        listFallbackPermissionManager.authorize(userId, listId, method).block()

        then:
        thrown(ResourceNotFoundException)

        1 * listRepository.findListById(listId) >> Mono.empty()
    }
}
