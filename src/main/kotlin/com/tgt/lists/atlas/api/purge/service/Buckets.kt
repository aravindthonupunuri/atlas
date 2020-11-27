package com.tgt.lists.atlas.api.purge.service

import io.micronaut.context.annotation.Requires
import mu.KotlinLogging
import javax.inject.Singleton

@Requires(property = "beacon.client.enabled", value = "true")
@Singleton
class Buckets {
    private val logger = KotlinLogging.logger { Buckets::class.java.name }
    companion object {
        fun getBuckets(): List<Int> {
            return listOf(0, 1, 2)
        }
    }
}
