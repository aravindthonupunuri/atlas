package com.tgt.lists.atlas.api.persistence.cassandra.internal

import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.core.CqlSession
import com.tgt.lists.micronaut.cassandra.CqlStmtsFileReader
import com.tgt.lists.micronaut.cassandra.DaoFactory
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Factory
import mu.KotlinLogging

@Factory
class GuestPreferenceDAOFactory(
    val cassConfig: CassConfig,
    val cqlSession: CqlSession
) : DaoFactory<GuestPreferenceDAO>(cassConfig.keyspace, cqlSession) {

    private val logger = KotlinLogging.logger {}

    init {
        logger.info("Started with cassConfig: $cassConfig")
        initSchema()
    }

    @Bean
    @Context // eager create this bean on startup to avoid exception for creating on cassandra io thread
    override fun instance(): GuestPreferenceDAO {
        val guestPreferenceMapperBuilder = GuestPreferenceMapperBuilder(cqlSession).build()
        val guestPreferenceMapperDAO = guestPreferenceMapperBuilder.guestPreferenceDao(CqlIdentifier.fromCql(cassConfig.keyspace))
        return guestPreferenceMapperDAO
    }

    private fun initSchema() {
        if (cassConfig.testMode) {
            // we initialize schema only during testing, but not in production
            val cqlStmtsFileReader = CqlStmtsFileReader("db/migration/V1__init_lists.cqlstmts", this::class.java)
            cqlStmtsFileReader.executeAllCqlStmts(cqlSession)
        }
    }
}