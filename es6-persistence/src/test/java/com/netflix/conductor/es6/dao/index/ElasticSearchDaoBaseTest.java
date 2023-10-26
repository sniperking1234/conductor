/*
 * Copyright 2022 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.es6.dao.index;

import java.net.InetAddress;
import java.util.concurrent.ExecutionException;

import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.client.transport.TransportClient;
import org.opensearch.cluster.metadata.IndexMetaData;
import org.opensearch.common.collect.ImmutableOpenMap;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.transport.TransportAddress;
import org.opensearch.transport.client.PreBuiltTransportClient;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.springframework.retry.support.RetryTemplate;

abstract class ElasticSearchDaoBaseTest extends ElasticSearchTest {

    protected TransportClient elasticSearchClient;
    protected ElasticSearchDAOV6 indexDAO;

    @Before
    public void setup() throws Exception {
        int mappedPort = container.getMappedPort(9300);
        properties.setUrl("tcp://localhost:" + mappedPort);

        Settings settings =
                Settings.builder().put("client.transport.ignore_cluster_name", true).build();

        elasticSearchClient =
                new PreBuiltTransportClient(settings)
                        .addTransportAddress(
                                new TransportAddress(
                                        InetAddress.getByName("localhost"), mappedPort));

        indexDAO =
                new ElasticSearchDAOV6(
                        elasticSearchClient, new RetryTemplate(), properties, objectMapper);
        indexDAO.setup();
    }

    @AfterClass
    public static void closeClient() {
        container.stop();
    }

    @After
    public void tearDown() {
        deleteAllIndices();

        if (elasticSearchClient != null) {
            elasticSearchClient.close();
        }
    }

    private void deleteAllIndices() {
        ImmutableOpenMap<String, IndexMetaData> indices =
                elasticSearchClient
                        .admin()
                        .cluster()
                        .prepareState()
                        .get()
                        .getState()
                        .getMetaData()
                        .getIndices();
        indices.forEach(
                cursor -> {
                    try {
                        elasticSearchClient
                                .admin()
                                .indices()
                                .delete(new DeleteIndexRequest(cursor.value.getIndex().getName()))
                                .get();
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
