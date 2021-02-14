package com.tgt.lists.atlas.persistence.cassandra

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.BaseFunctionalTest
import com.tgt.lists.atlas.api.domain.model.entity.GuestPreferenceEntity
import com.tgt.lists.atlas.api.persistence.cassandra.GuestPreferenceRepository
import com.tgt.lists.atlas.util.ListDataProvider
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Stepwise
import spock.lang.Unroll

import javax.inject.Inject

@MicronautTest
@Stepwise
class GuestPreferenceRepositoryFunctionalTest extends BaseFunctionalTest {

    static final Logger logger = LoggerFactory.getLogger(GuestPreferenceRepositoryFunctionalTest)

    @Inject
    GuestPreferenceRepository guestPreferenceRepository

    @Shared
    ListDataProvider dataProvider = new ListDataProvider()

    @Shared
    List<String> listIds = [Uuids.timeBased().toString(),
                            Uuids.timeBased().toString(),
                            Uuids.timeBased().toString(),
                            Uuids.timeBased().toString()]

    @Shared
    List<String> guestIds = ["126890467", "126890367", "126890567", "531244530"]

    def setup() {
    }

    @Unroll
    def "test create new guest preference"() {
        given:
        GuestPreferenceEntity guestPreferenceEntity = dataProvider.createGuestPreferenceEntity(guestId, listSortOrder)

        when:
        guestPreferenceRepository.saveGuestPreference(guestPreferenceEntity).block()

        then:
        notThrown(Throwable)

        where:
        guestId     |   listSortOrder
        guestIds[0] |   listIds[0]
        guestIds[1] |   listIds[1].plus(",").plus(listIds[3])
        guestIds[2] |   listIds[2].plus(",").plus(listIds[3]).plus(",").plus(listIds[1])
        guestIds[3] |   listIds[3].plus(",").plus(listIds[0]).plus(",").plus(listIds[2]).plus(",").plus(listIds[1])
    }

    @Unroll
    def "test get preference by guest id"() {

        when:
        GuestPreferenceEntity guestPreferenceEntity = guestPreferenceRepository.findGuestPreference(guestId).block()

        then:
        guestPreferenceEntity != null
        guestPreferenceEntity.guestId == guestId
        guestPreferenceEntity.listSortOrder == listSortOrder

        where:
        guestId     |   listSortOrder
        guestIds[0] |   listIds[0]
        guestIds[1] |   listIds[1].plus(",").plus(listIds[3])
        guestIds[2] |   listIds[2].plus(",").plus(listIds[3]).plus(",").plus(listIds[1])
        guestIds[3] |   listIds[3].plus(",").plus(listIds[0]).plus(",").plus(listIds[2]).plus(",").plus(listIds[1])
    }
}
