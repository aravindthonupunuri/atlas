package com.tgt.lists.atlas.persistence.cassandra

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.BaseFunctionalTest
import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.util.ListDataProvider
import com.tgt.lists.micronaut.cassandra.DaoInstrumenter
import com.tgt.lists.micronaut.cassandra.DatabaseExecTestListener
import io.micronaut.test.annotation.MicronautTest
import org.jetbrains.annotations.NotNull
import reactor.core.publisher.Mono
import spock.lang.Shared
import spock.lang.Stepwise

import javax.inject.Inject
import java.time.Instant

@MicronautTest
@Stepwise
class DataBaseTimeOutFunctionalTest extends BaseFunctionalTest {

    @Inject
    ListRepository listsRepository

    @Inject
    DaoInstrumenter daoInstrumenter

    @Shared
    ListDataProvider dataProvider = new ListDataProvider()

    @Shared
    boolean executeTimeout = false

    def setup() {
        daoInstrumenter.attachTestListener(new DatabaseExecTestListener() {
            @Override
            boolean shouldTimeout(@NotNull String daoName, @NotNull String methodName) {
                return executeTimeout
            }
        })
    }

    def "test timeout handling, updateList batch statement method alone times out"() {
        given:
        def listId = Uuids.timeBased()
        ListEntity createdListEntity = dataProvider.createListEntity(listId, "time- Out", "shopping", "s", "126890567", "d", Instant.now(), Instant.now())

        def updateDescription = "modified description"
        def updateNotes = "modified Notes"
        // Pre-requisite
        listsRepository.saveList(createdListEntity).block()

        ListEntity existingListEntity = listsRepository.findListById(listId).block()
        ListEntity updateListEntity = new ListEntity(listId,
                existingListEntity.title,
                existingListEntity.type,
                existingListEntity.subtype,
                existingListEntity.guestId,
                updateDescription,
                existingListEntity.channel,
                existingListEntity.subchannel,
                existingListEntity.marker,
                existingListEntity.location,
                updateNotes,
                existingListEntity.state,
                null,
                null,
                null,
                existingListEntity.createdAt,
                null,
                Boolean.FALSE
        )

        daoInstrumenter.attachTestListener(new DatabaseExecTestListener() {
            @Override
            boolean shouldTimeout(@NotNull String daoName, @NotNull String methodName) {
                if (methodName == "updateList")
                    return true
                else return false
            }
        })

        when:
        def result = listsRepository.updateList(existingListEntity, updateListEntity)
                .onErrorResume { Mono.just(existingListEntity) }.block()

        then:
        result == existingListEntity
    }
}
