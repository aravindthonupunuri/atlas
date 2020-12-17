package com.tgt.lists.atlas.purge.persistence.cassandra.internal

import com.tgt.lists.micronaut.cassandra.InstrumentedDao
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import javax.inject.Named

@Requires(property = "beacon.client.enabled", value = "true")
@Primary
@Named("PurgeInstrumentedDAO")
@InstrumentedDao("PurgeDAO")
interface PurgeInstrumentedDAO : PurgeDAO