package com.tgt.lists.atlas


import com.datastax.oss.driver.api.core.CqlSession
import io.micronaut.test.support.TestPropertyProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.CassandraContainer
import org.testcontainers.containers.GenericContainer
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

class BaseFunctionalTest extends Specification implements TestPropertyProvider {
    static final Logger logger = LoggerFactory.getLogger(BaseFunctionalTest)

    @Shared
    // using rinscy/cassandra from https://github.com/saidbouras/cassandra-docker-unit
    // instead of testcontainer's CassandraContainer image because of faster startup time
    // rinscy/cassandra has ~11 seconds faster startup
    static GenericContainer cassandra =
            new CassandraContainer("rinscy/cassandra:3.11")
//                    .withClasspathResourceMapping("cassandra.yaml",
//                            "/app/cassandra.yaml", BindMode.READ_ONLY)
//                    .withCommand("-Dcassandra.config=/app/cassandra.yaml")

    @Shared
    static boolean cassandraStarted = false

    @Inject
    CqlSession cqlSession

    /*
    These properties will override application.yml defined properties
    */
    @Override
    Map<String, String> getProperties() {

        Map<String, String> properties = ["cassandra.default.clusterName" : "datacenter1",
                                          "tracing.zipkin.enabled"        : true
        ]

        String contactPoints = System.getenv("CASSANDRA_URL")
        if(contactPoints == null) {
            // use local cassandra
            logger.info("Local cassandra mode")
            if (!cassandraStarted) {
                cassandra.start()
                cassandraStarted = true
//                sleep(8000) // delay required with cassandra authentication for test to work
            }
            def mappedPort = cassandra.getMappedPort(9042)
            contactPoints = "127.0.0.1:${mappedPort}"
        }
        else {
            // using external cassandra in drone
            properties["cassandra.default.advanced.auth-provider.class"] = null
        }

        logger.info("Using cassandra url: $contactPoints")

        properties["cassandra.default.basic.contact-points"] = [contactPoints]

        return properties
    }
}

