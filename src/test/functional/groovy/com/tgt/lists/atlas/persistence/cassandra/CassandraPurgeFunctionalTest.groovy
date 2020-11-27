package com.tgt.lists.atlas.persistence.cassandra

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.BaseFunctionalTest
import com.tgt.lists.atlas.api.domain.model.entity.GuestListEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListItemEntity
import com.tgt.lists.atlas.api.domain.model.entity.ListItemExtEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.type.ItemType
import com.tgt.lists.atlas.api.type.LIST_ITEM_STATE
import com.tgt.lists.atlas.api.purge.persistence.cassandra.PurgeRepository
import com.tgt.lists.atlas.api.purge.persistence.entity.PurgeEntity
import com.tgt.lists.atlas.util.ListDataProvider
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Stepwise
import spock.lang.Unroll

import javax.inject.Inject
import java.time.Instant
import java.time.LocalDate

@MicronautTest
@Stepwise
class CassandraPurgeFunctionalTest extends BaseFunctionalTest {

    @Inject
    PurgeRepository purgeRepository

    @Shared
    ListDataProvider dataProvider = new ListDataProvider()

    @Shared
    List<UUID> listIds = [Uuids.timeBased(), Uuids.timeBased(), Uuids.timeBased(), Uuids.timeBased(), Uuids.timeBased(), Uuids.timeBased()]

    def setup() {
    }

    @Unroll
    def "test add lists expiration"() {
        given:
        PurgeEntity purgeEntity = dataProvider.createPurgeEntity(listId, bucket, expiration)

        when:
        purgeRepository.savePurgeExpiration(purgeEntity).block()

        then:
        notThrown(Throwable)

        where:
        listId       | bucket  | expiration
        listIds[0]   | 0       | LocalDate.of(2100, 03, 01)
        listIds[1]   | 1       | LocalDate.of(2100, 03, 01)
        listIds[2]   | 2       | LocalDate.of(2100, 03, 01)
        listIds[3]   | 1       | LocalDate.of(2100, 05, 02)
        listIds[4]   | 0       | LocalDate.of(2100, 05, 02)
        listIds[5]   | 2       | LocalDate.of(2100, 05, 02)
    }

    def "test findPurgeExpirationByListId"() {
        when:
        PurgeEntity purgeEntity  = purgeRepository.findPurgeExpirationByListId(LocalDate.of(2100, 03, 01), listIds[0]).block()

        then:
        purgeEntity.listId == listIds[0]
    }

    def "test savePurgeExpiration"() {
        given:
        UUID listId = Uuids.timeBased()
        PurgeEntity purgeEntity = dataProvider.createPurgeEntity(listId, 1, LocalDate.of(2100, 05, 02))

        when:
        purgeRepository.savePurgeExpiration(purgeEntity).block()

        then:
        notThrown(Throwable)
    }

    def "test findPurgeExpiration with expiration 2100-05-02 after adding a new purge entity"() {
        when:
        List<PurgeEntity> purgeEntityList = purgeRepository.findPurgeExpiration(LocalDate.of(2100, 05, 02)).collectList().block()

        then:
        !purgeEntityList.isEmpty()
        purgeEntityList.size() == 4
    }
}

