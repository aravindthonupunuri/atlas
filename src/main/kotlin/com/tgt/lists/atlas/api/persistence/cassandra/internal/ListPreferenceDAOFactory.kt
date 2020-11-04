package com.tgt.lists.atlas.api.persistence.cassandra.internal

import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.core.CqlSession
import com.tgt.lists.micronaut.cassandra.CqlStmtsFileReader
import com.tgt.lists.micronaut.cassandra.DaoFactory
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Value

@Factory
class ListPreferenceDAOFactory(@Value("\${lists-cassandra.keyspace}") val keyspace: String, val cqlSession: CqlSession) : DaoFactory<ListPreferenceDAO>(keyspace, cqlSession) {

    init {
        initSchema()
    }

    @Bean
    @Context
    override fun instance(): ListPreferenceDAO {
        val listPreferenceMapperBuilder = ListPreferenceMapperBuilder(cqlSession).build()
        val listPreferenceDAO = listPreferenceMapperBuilder.listPreferenceDao(CqlIdentifier.fromCql(keyspace))
        return listPreferenceDAO
    }

    private fun initSchema() {
        val cqlStmtsFileReader = CqlStmtsFileReader("db/migration/V1__init_lists.cqlstmts", this::class.java)
        cqlStmtsFileReader.executeAllCqlStmts(cqlSession)
    }
}