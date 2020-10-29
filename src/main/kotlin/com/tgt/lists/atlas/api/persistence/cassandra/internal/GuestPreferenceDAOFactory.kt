package com.tgt.lists.atlas.api.persistence.cassandra.internal

import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.core.CqlSession
import com.tgt.lists.micronaut.cassandra.CqlStmtsFileReader
import com.tgt.lists.micronaut.cassandra.DaoFactory
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Value

@Factory
class GuestPreferenceDAOFactory(@Value("\${lists-cassandra.keyspace}") val keyspace: String, val cqlSession: CqlSession) : DaoFactory<GuestPreferenceDAO>(keyspace, cqlSession) {

    init {
        initSchema()
    }

    @Bean
    override fun instance(): GuestPreferenceDAO {
        val guestPreferenceMapperBuilder = GuestPreferenceMapperBuilder(cqlSession).build()
        val guestPreferenceMapperDAO = guestPreferenceMapperBuilder.guestPreferenceDao(CqlIdentifier.fromCql(keyspace))
        return guestPreferenceMapperDAO
    }

    private fun initSchema() {
        val cqlStmtsFileReader = CqlStmtsFileReader("db/migration/V1__init_lists.cqlstmts", this::class.java)
        cqlStmtsFileReader.executeAllCqlStmts(cqlSession)
    }
}