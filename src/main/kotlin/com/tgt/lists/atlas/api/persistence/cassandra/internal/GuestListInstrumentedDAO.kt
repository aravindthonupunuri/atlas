package com.tgt.lists.atlas.api.persistence.cassandra.internal

import com.tgt.lists.micronaut.cassandra.InstrumentedDao
import io.micronaut.context.annotation.Primary
import javax.inject.Named

@Primary
@Named("GuestListInstrumentedDAO")
@InstrumentedDao("GuestListDAO")
interface GuestListInstrumentedDAO : GuestListDAO