package com.tgt.lists.atlas.persistence.cassandra

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.BaseFunctionalTest
import com.tgt.lists.atlas.purge.persistence.cassandra.PurgeRepository
import com.tgt.lists.atlas.purge.persistence.entity.PurgeEntity
import com.tgt.lists.atlas.util.ListDataProvider
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Stepwise
import spock.lang.Unroll

import javax.inject.Inject
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

    @Shared List<LocalDate> localDateList = [LocalDate.of(2300, 03, 01), LocalDate.of(2300, 05, 02)]

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
        listIds[0]   | 0       | localDateList[0]
        listIds[1]   | 1       | localDateList[0]
        listIds[2]   | 2       | localDateList[0]
        listIds[3]   | 1       | localDateList[1]
        listIds[4]   | 0       | localDateList[1]
        listIds[5]   | 2       | localDateList[1]
    }

    def "test findPurgeExpirationByListId"() {
        when:
        PurgeEntity purgeEntity  = purgeRepository.findPurgeExpirationByListId(localDateList[0], listIds[0]).block()

        then:
        purgeEntity.listId == listIds[0]
    }

    def "test savePurgeExpiration"() {
        given:
        UUID listId = Uuids.timeBased()
        PurgeEntity purgeEntity = dataProvider.createPurgeEntity(listId, 1, localDateList[1])

        when:
        purgeRepository.savePurgeExpiration(purgeEntity).block()

        then:
        notThrown(Throwable)
    }

    def "test findPurgeExpiration with expiration 2300-05-02 after adding a new purge entity"() {
        when:
        List<PurgeEntity> purgeEntityList = purgeRepository.findPurgeExpiration(localDateList[1]).collectList().block()

        then:
        !purgeEntityList.isEmpty()
        purgeEntityList.size() == 4
    }
}

