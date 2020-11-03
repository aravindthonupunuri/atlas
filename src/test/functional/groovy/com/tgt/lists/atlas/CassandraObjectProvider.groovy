package com.tgt.lists.atlas

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet
import com.datastax.dse.driver.api.mapper.reactive.MappedReactiveResultSet
import com.datastax.oss.driver.api.core.AllNodesFailedException
import com.datastax.oss.driver.api.core.ConsistencyLevel
import com.datastax.oss.driver.api.core.DriverException
import com.datastax.oss.driver.api.core.Version
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder
import com.datastax.oss.driver.api.core.cql.ColumnDefinitions
import com.datastax.oss.driver.api.core.cql.ExecutionInfo
import com.datastax.oss.driver.api.core.loadbalancing.NodeDistance
import com.datastax.oss.driver.api.core.metadata.EndPoint
import com.datastax.oss.driver.api.core.metadata.Node
import com.datastax.oss.driver.api.core.metadata.NodeState
import com.datastax.oss.driver.api.core.servererrors.*
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

import java.util.function.Function

/**
 * Provides objects related to Cassandra and its driver
 */
class CassandraObjectProvider {
    ReadTimeoutException buildReadTimeoutException(ConsistencyLevel consistencyLevel, int received, int blockFor, boolean dataPresent) {
        return new ReadTimeoutException(buildNode(NodeState.UP), consistencyLevel, received, blockFor, dataPresent)
    }

    WriteTimeoutException buildWriteTimeoutException(ConsistencyLevel consistencyLevel, int received, int blockFor, WriteType writeType) {
        return new WriteTimeoutException(buildNode(NodeState.UP), consistencyLevel, received, blockFor, writeType)
    }

    UnavailableException buildUnavailableException(ConsistencyLevel consistencyLevel, int required, int alive) {
        return new UnavailableException(buildNode(NodeState.UP), consistencyLevel, required, alive)
    }

    AllNodesFailedException buildAllNodesFailedException(List<QueryExecutionException> wrappedExceptions) {
        Map<Node, Throwable> exceptionMap = [:]
        wrappedExceptions.each { ex ->
            Node node = buildNode(NodeState.UP)
            exceptionMap.put(node, ex)
        }

        List<Map.Entry<Node, Throwable>> errors = []
        exceptionMap.entrySet().each { entry ->
            errors.add(entry)
        }

        return AllNodesFailedException.fromErrors(errors)
    }

    /**
     * Returns a 3-node map with all nodes up
     * @return
     */
    Map<UUID, Node> clusterWithAllNodesUp() {
        Node node1Up = buildNode(NodeState.UP)
        Node node2Up = buildNode(NodeState.UP)
        Node node3Up = buildNode(NodeState.UP)

        Map<UUID, Node> nodeMap = [:]
        nodeMap.put(UUID.randomUUID(), node1Up)
        nodeMap.put(UUID.randomUUID(), node2Up)
        nodeMap.put(UUID.randomUUID(), node3Up)
        return nodeMap
    }

    /**
     * Returns a 3-node map with 2 nodes up
     * @return
     */
    Map<UUID, Node> clusterWithTwoNodesUp() {
        Node node1Up = buildNode(NodeState.UP)
        Node node2Up = buildNode(NodeState.UP)
        Node node3Down = buildNode(NodeState.DOWN)

        Map<UUID, Node> nodeMap = [:]
        nodeMap.put(UUID.randomUUID(), node1Up)
        nodeMap.put(UUID.randomUUID(), node2Up)
        nodeMap.put(UUID.randomUUID(), node3Down)
        return nodeMap
    }

    /**
     * Returns a 3-node map with 1 node up
     * @return
     */
    Map<UUID, Node> clusterWithOneNodeUp() {
        Node node1Up = buildNode(NodeState.UP)
        Node node2Down = buildNode(NodeState.DOWN)
        Node node3Down = buildNode(NodeState.DOWN)

        Map<UUID, Node> nodeMap = [:]
        nodeMap.put(UUID.randomUUID(), node1Up)
        nodeMap.put(UUID.randomUUID(), node2Down)
        nodeMap.put(UUID.randomUUID(), node3Down)
        return nodeMap
    }

    /**
     * Returns a 3-node map with no nodes up
     * @return
     */
    Map<UUID, Node> clusterWithNoNodesUp() {
        Node node1Down = buildNode(NodeState.DOWN)
        Node node2Down = buildNode(NodeState.DOWN)
        Node node3Down = buildNode(NodeState.DOWN)

        Map<UUID, Node> nodeMap = [:]
        nodeMap.put(UUID.randomUUID(), node1Down)
        nodeMap.put(UUID.randomUUID(), node2Down)
        nodeMap.put(UUID.randomUUID(), node3Down)
        return nodeMap
    }

    Node buildNode(NodeState nodeState) {
        return new Node() {
            @Override
            EndPoint getEndPoint() {
                return null
            }

            @Override
            Optional<InetSocketAddress> getBroadcastRpcAddress() {
                return null
            }

            @Override
            Optional<InetSocketAddress> getBroadcastAddress() {
                return null
            }

            @Override
            Optional<InetSocketAddress> getListenAddress() {
                return null
            }

            @Override
            String getDatacenter() {
                return null
            }

            @Override
            String getRack() {
                return null
            }

            @Override
            Version getCassandraVersion() {
                return null
            }

            @Override
            Map<String, Object> getExtras() {
                return null
            }

            @Override
            NodeState getState() {
                return nodeState
            }

            @Override
            long getUpSinceMillis() {
                return 0
            }

            @Override
            int getOpenConnections() {
                return 0
            }

            @Override
            boolean isReconnecting() {
                return false
            }

            @Override
            NodeDistance getDistance() {
                return null
            }

            @Override
            UUID getHostId() {
                return null
            }

            @Override
            UUID getSchemaVersion() {
                return null
            }
        }
    }

    MappedReactiveResultSet getErrorMappedReactiveResultSet(DriverException driverException) {
        return new MappedReactiveResultSet() {
            @Override
            void subscribe(Subscriber s) {
                s.onSubscribe(new Subscription() {
                    @Override
                    void request(long n) {
                        s.onError(driverException)
                    }

                    @Override
                    void cancel() {}
                })
            }

            @Override
            Publisher<? extends ColumnDefinitions> getColumnDefinitions() {
                return null
            }

            @Override
            Publisher<? extends ExecutionInfo> getExecutionInfos() {
                return null
            }

            @Override
            Publisher<Boolean> wasApplied() {
                return null
            }
        }
    }

    MappedReactiveResultSet getSuccessMappedReactiveResultSet(Object successData) {
        return new MappedReactiveResultSet() {
            @Override
            void subscribe(Subscriber s) {
                s.onSubscribe(new Subscription() {
                    @Override
                    void request(long n) {
                        s.onNext(successData)
                        s.onComplete()
                    }

                    @Override
                    void cancel() {}
                })
            }

            @Override
            Publisher<? extends ColumnDefinitions> getColumnDefinitions() {
                return null
            }

            @Override
            Publisher<? extends ExecutionInfo> getExecutionInfos() {
                return null
            }

            @Override
            Publisher<Boolean> wasApplied() {
                return null
            }
        }
    }

    ReactiveResultSet getErrorReactiveResultSet(DriverException driverException) {
        return new ReactiveResultSet() {
            @Override
            void subscribe(Subscriber s) {
                s.onSubscribe(new Subscription() {
                    @Override
                    void request(long n) {
                        s.onError(driverException)
                    }

                    @Override
                    void cancel() {}
                })
            }

            @Override
            Publisher<? extends ColumnDefinitions> getColumnDefinitions() {
                return null
            }

            @Override
            Publisher<? extends ExecutionInfo> getExecutionInfos() {
                return null
            }

            @Override
            Publisher<Boolean> wasApplied() {
                return null
            }
        }
    }

    ReactiveResultSet getSuccessReactiveResultSet(Object successData) {
        return new ReactiveResultSet() {
            @Override
            void subscribe(Subscriber s) {
                s.onSubscribe(new Subscription() {
                    @Override
                    void request(long n) {
                        s.onNext(successData)
                        s.onComplete()
                    }

                    @Override
                    void cancel() {}
                })
            }

            @Override
            Publisher<? extends ColumnDefinitions> getColumnDefinitions() {
                return null
            }

            @Override
            Publisher<? extends ExecutionInfo> getExecutionInfos() {
                return null
            }

            @Override
            Publisher<Boolean> wasApplied() {
                return null
            }
        }
    }

    Function getConsistencySetterFn(ConsistencyLevel downgradedConsistencyLevel) {
        new Function() {
            @Override
            Object apply(Object builder) {
                return ((BoundStatementBuilder)builder).setConsistencyLevel(downgradedConsistencyLevel)
            }
        }
    }
}
