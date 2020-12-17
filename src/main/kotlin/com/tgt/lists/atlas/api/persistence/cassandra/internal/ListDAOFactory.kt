package com.tgt.lists.atlas.api.persistence.cassandra.internal

import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.core.CqlSession
import com.tgt.lists.micronaut.cassandra.CqlStmtsFileReader
import com.tgt.lists.micronaut.cassandra.DaoFactory
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory

@Factory
class ListDAOFactory(
    val cassConfig: CassConfig,
    val cqlSession: CqlSession
) : DaoFactory<ListDAO>(cassConfig.keyspace, cqlSession) {

    init {
        initSchema()
    }

    @Bean
    override fun instance(): ListDAO {
        val listMapperBuilder = ListMapperBuilder(cqlSession).build()
        val listDAO = listMapperBuilder.listsDao(CqlIdentifier.fromCql(cassConfig.keyspace))
        return listDAO
    }

    private fun initSchema() {
        if (cassConfig.testMode) {
            val cqlStmtsFileReader = CqlStmtsFileReader("db/migration/V1__init_lists.cqlstmts", this::class.java)
            cqlStmtsFileReader.executeAllCqlStmts(cqlSession)
        }
    }
}