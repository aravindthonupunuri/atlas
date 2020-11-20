package com.tgt.lists.atlas.api.util

import io.micronaut.http.context.ServerRequestContext

/**
 * Evaluates if current request is an HttpRequest with test flag header, to identify this request
 * as a test request (e.g. a performance testing request)
 */
class TestListEvaluator {
    companion object {
        const val TEST_LIST_HEADER = "x-test-list"

        @JvmStatic
        fun evaluate() : Boolean {
            return ServerRequestContext.currentRequest<Any>().map {
                val testHdr: String? = it.headers.get(TEST_LIST_HEADER)
                testHdr?.let { true } ?: false
            }.orElse(false)
        }
    }
}