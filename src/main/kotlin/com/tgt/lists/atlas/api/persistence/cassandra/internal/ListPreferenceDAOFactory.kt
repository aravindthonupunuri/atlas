package com.tgt.lists.atlas.api.persistence.cassandra.internal

import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.core.CqlSession
import com.tgt.lists.micronaut.cassandra.CqlStmtsFileReader
import com.tgt.lists.micronaut.cassandra.DaoFactory
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import mu.KotlinLogging

@Factory
class ListPreferenceDAOFactory(
    val cassConfig: CassConfig,
    val cqlSession: CqlSession
) : DaoFactory<ListPreferenceDAO>(cassConfig.keyspace, cqlSession) {

    private val logger = KotlinLogging.logger {}

    init {
        logger.info("Started with cassConfig: $cassConfig")
        initSchema()
    }

    @Bean
    override fun instance(): ListPreferenceDAO {
        val listPreferenceMapperBuilder = ListPreferenceMapperBuilder(cqlSession).build()
        val listPreferenceDAO = listPreferenceMapperBuilder.listPreferenceDao(CqlIdentifier.fromCql(cassConfig.keyspace))
        return listPreferenceDAO
    }

    private fun initSchema() {
        if (cassConfig.testMode) {
            // we initialize schema only during testing, but not in production
            val cqlStmtsFileReader = CqlStmtsFileReader("db/migration/V1__init_lists.cqlstmts", this::class.java)
            cqlStmtsFileReader.executeAllCqlStmts(cqlSession)
        }
    }
}