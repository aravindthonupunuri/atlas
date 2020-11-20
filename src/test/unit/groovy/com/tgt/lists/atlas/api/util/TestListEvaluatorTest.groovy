package com.tgt.lists.atlas.api.util

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.context.ServerRequestContext
import spock.lang.Specification

class TestListEvaluatorTest extends Specification {

    TestListEvaluator testListEvaluator
    HttpHeaders headers
    HttpRequest httpRequest

    def setup() {
        headers = Mock(HttpHeaders)
        httpRequest = Mock(HttpRequest)
        testListEvaluator = new TestListEvaluator()
    }

    def "test flag=true"() {
        given:
        headers.get(TestListEvaluator.TEST_LIST_HEADER) >> "true"
        httpRequest.headers >> headers
        ServerRequestContext.set(httpRequest)

        when:
        def result = testListEvaluator.evaluate()

        then:
        result
    }

    def "test flag=false with http request"() {
        given:
        httpRequest.headers >> headers
        ServerRequestContext.set(httpRequest)

        when:
        def result = testListEvaluator.evaluate()

        then:
        !result
    }

    def "test without http request"() {
        given:
        ServerRequestContext.set(null)

        when:
        def result = testListEvaluator.evaluate()

        then:
        !result
    }
}
