package com.tgt.lists.atlas.persistence.cassandra

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.tgt.lists.atlas.BaseFunctionalTest
import com.tgt.lists.atlas.api.domain.model.entity.ListPreferenceEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListPreferenceRepository
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
class ListPreferenceRepositoryFunctionalTest extends BaseFunctionalTest {

    static final Logger logger = LoggerFactory.getLogger(GuestPreferenceRepositoryFunctionalTest)

    @Inject
    ListPreferenceRepository listPreferenceRepository

    @Shared
    ListDataProvider dataProvider = new ListDataProvider()

    @Shared
    List<UUID> listIds = [Uuids.timeBased(), Uuids.timeBased(), Uuids.timeBased(), Uuids.timeBased()]

    @Shared
    List<String> listItemIds = [Uuids.timeBased().toString(),
                              Uuids.timeBased().toString(),
                              Uuids.timeBased().toString(),
                              Uuids.timeBased().toString()]

    @Shared
    List<String> guestIds = ["126890367", "126890467", "126890567", "531244530"]

    def setup() {
    }

    @Unroll
    def "test create new list item preference"() {
        given:
        ListPreferenceEntity listPreferenceEntity = dataProvider.createListPreferenceEntity(listId, guestId, itemSortOrder)

        when:
        listPreferenceRepository.saveListPreference(listPreferenceEntity).block()

        then:
        notThrown(Throwable)

        where:
        guestId     |   listId      |   itemSortOrder
        guestIds[0] |   listIds[0]  |   listItemIds[0]
        guestIds[1] |   listIds[1]  |   listItemIds[1].plus(",").plus(listItemIds[0])
        guestIds[2] |   listIds[2]  |   listItemIds[0].plus(",").plus(listItemIds[2]).plus(",").plus(listItemIds[1])
        guestIds[3] |   listIds[3]  |   listItemIds[0].plus(",").plus(listItemIds[2]).plus(",").plus(listItemIds[1]).plus(",").plus(listItemIds[3])
    }

    @Unroll
    def "test get preference by list and guest id"() {
        when:
        ListPreferenceEntity listPreferenceEntity = listPreferenceRepository.getListPreference(listId, guestId).block()

        then:
        listPreferenceEntity != null
        listPreferenceEntity.guestId == guestId
        listPreferenceEntity.listId == listId
        listPreferenceEntity.itemSortOrder == itemSortOrder

        where:
        guestId     |   listId      |   itemSortOrder
        guestIds[0] |   listIds[0]  |   listItemIds[0]
        guestIds[1] |   listIds[1]  |   listItemIds[1].plus(",").plus(listItemIds[0])
        guestIds[2] |   listIds[2]  |   listItemIds[0].plus(",").plus(listItemIds[2]).plus(",").plus(listItemIds[1])
        guestIds[3] |   listIds[3]  |   listItemIds[0].plus(",").plus(listItemIds[2]).plus(",").plus(listItemIds[1]).plus(",").plus(listItemIds[3])
    }

    @Unroll
    def "test delete preference by list and guest id"() {
        given:
        ListPreferenceEntity listPreferenceEntity = dataProvider.getListPreferenceEntity(listId, guestId)

        when:
        listPreferenceRepository.deleteListPreferenceByListAndGuestId(listPreferenceEntity).block()

        //Validate if it really is deleted
        ListPreferenceEntity listPreference = listPreferenceRepository.getListPreference(listId, guestId).block()

        then:
        listPreference == null

        where:
        guestId     |   listId
        guestIds[0] |   listIds[0]
        guestIds[1] |   listIds[1]
        guestIds[2] |   listIds[2]
        guestIds[3] |   listIds[3]
    }
}
