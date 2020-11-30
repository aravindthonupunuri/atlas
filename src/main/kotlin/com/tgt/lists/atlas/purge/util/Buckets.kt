package com.tgt.lists.atlas.purge.util

import io.micronaut.context.annotation.Requires
import mu.KotlinLogging
import java.time.LocalDateTime
import javax.inject.Singleton

@Requires(property = "beacon.client.enabled", value = "true")
@Singleton
class Buckets {
    private val logger = KotlinLogging.logger { Buckets::class.java.name }
    companion object {
        fun getBuckets(): List<Int> {
            return listOf(0, 1, 2)
        }
        fun getBucket(localDateTime: LocalDateTime): Int {
            return localDateTime.hour % getBuckets().size
        }
    }
}
