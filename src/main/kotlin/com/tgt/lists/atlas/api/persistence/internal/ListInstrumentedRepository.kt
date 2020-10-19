package com.tgt.lists.atlas.api.persistence.internal

import com.tgt.lists.atlas.api.persistence.ListRepository
import com.tgt.lists.micronaut.persistence.instrumentation.InstrumentedRepository
import io.micronaut.context.annotation.Primary

@Primary // make it primary to instrument ListCrudRepository
@InstrumentedRepository("ListCrudRepository")
interface ListInstrumentedRepository : ListRepository
