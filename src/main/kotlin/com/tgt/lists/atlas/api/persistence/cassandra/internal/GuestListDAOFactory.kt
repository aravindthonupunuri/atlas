package com.tgt.lists.atlas.api.persistence.cassandra.internal

import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.core.CqlSession
import com.tgt.lists.micronaut.cassandra.CqlStmtsFileReader
import com.tgt.lists.micronaut.cassandra.DaoFactory
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Factory

@Factory
class GuestListDAOFactory(
    val cassConfig: CassConfig,
    val cqlSession: CqlSession
) : DaoFactory<GuestListDAO>(cassConfig.keyspace, cqlSession) {

    init {
        initSchema()
    }

    @Bean
    @Context
    override fun instance(): GuestListDAO {
        val guestListMapperBuilder = GuestListMapperBuilder(cqlSession).build()
        val guestListDAO = guestListMapperBuilder.guestListsDao(CqlIdentifier.fromCql(cassConfig.keyspace))
        return guestListDAO
    }

    private fun initSchema() {
        if (cassConfig.testMode) {
            val cqlStmtsFileReader = CqlStmtsFileReader("db/migration/V1__init_lists.cqlstmts", this::class.java)
            cqlStmtsFileReader.executeAllCqlStmts(cqlSession)
        }
    }
}