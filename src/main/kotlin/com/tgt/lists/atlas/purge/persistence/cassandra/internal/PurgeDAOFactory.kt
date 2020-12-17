package com.tgt.lists.atlas.purge.persistence.cassandra.internal

import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.core.CqlSession
import com.tgt.lists.micronaut.cassandra.DaoFactory
import com.tgt.lists.atlas.api.persistence.cassandra.internal.CassConfig
import com.tgt.lists.micronaut.cassandra.CqlStmtsFileReader
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import mu.KotlinLogging

@Requires(property = "beacon.client.enabled", value = "true")
@Factory
class PurgeDAOFactory(
    val cassConfig: CassConfig,
    val cqlSession: CqlSession
) : DaoFactory<PurgeDAO>(cassConfig.keyspace, cqlSession) {

    private val logger = KotlinLogging.logger {}

    init {
        logger.info("Started with cassConfig: $cassConfig")
        initSchema()
    }

    @Bean
    override fun instance(): PurgeDAO {
        val purgeMapperBuilder = PurgeMapperBuilder(cqlSession).build()
        val purgeDAO = purgeMapperBuilder.purgeDAO(CqlIdentifier.fromCql(cassConfig.keyspace))
        return purgeDAO
    }

    private fun initSchema() {
        if (cassConfig.testMode) {
            // we initialize schema only during testing, but not in production
            val cqlStmtsFileReader = CqlStmtsFileReader("db/migration/V1__init_lists.cqlstmts", this::class.java)
            cqlStmtsFileReader.executeAllCqlStmts(cqlSession)
        }
    }
}