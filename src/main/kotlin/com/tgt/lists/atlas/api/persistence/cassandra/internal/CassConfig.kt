package com.tgt.lists.atlas.api.persistence.cassandra.internal

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.bind.annotation.Bindable
import javax.validation.constraints.NotBlank

@ConfigurationProperties("lists-cassandra")
interface CassConfig {

    @get:NotBlank
    val keyspace: String

    @get:Bindable(defaultValue = "false")
    val testMode: Boolean
}