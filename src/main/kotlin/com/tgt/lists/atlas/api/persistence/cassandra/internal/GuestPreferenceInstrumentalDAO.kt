package com.tgt.lists.atlas.api.persistence.cassandra.internal

import com.tgt.lists.micronaut.cassandra.InstrumentedDao
import io.micronaut.context.annotation.Primary

@Primary
@InstrumentedDao("GuestPreferenceDAO")
interface GuestPreferenceInstrumentalDAO : GuestPreferenceDAO