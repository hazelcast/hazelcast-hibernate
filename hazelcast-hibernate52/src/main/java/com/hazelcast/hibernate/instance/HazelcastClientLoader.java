/*
 * Copyright 2020 Hazelcast Inc.
 *
 * Licensed under the Hazelcast Community License (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://hazelcast.com/hazelcast-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.hazelcast.hibernate.instance;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.core.HazelcastException;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.hibernate.CacheEnvironment;
import org.hibernate.cache.CacheException;
import org.hibernate.internal.util.config.ConfigurationHelper;

import java.io.IOException;
import java.util.Properties;

/**
 * A factory implementation to build up a {@link HazelcastInstance}
 * implementation using {@link HazelcastClient}.
 */
class HazelcastClientLoader implements IHazelcastInstanceLoader {

    private static final int INITIAL_BACKOFF_MS = 2000;
    private static final int MAX_BACKOFF_MS = 35000;
    private static final double BACKOFF_MULTIPLIER = 1.5D;

    private HazelcastInstance client;
    private ClientConfig clientConfig;
    private String instanceName;

    @Override
    public void configure(final Properties props) {
        instanceName = ConfigurationHelper.getString(CacheEnvironment.NATIVE_CLIENT_INSTANCE_NAME, props, null);
        if (instanceName != null) {
            return;
        }

        String address = ConfigurationHelper.getString(CacheEnvironment.NATIVE_CLIENT_ADDRESS, props, null);
        String clientClusterName = ConfigurationHelper.getString(CacheEnvironment.NATIVE_CLIENT_CLUSTER_NAME, props, null);
        String configResourcePath = CacheEnvironment.getConfigFilePath(props);

        if (configResourcePath != null) {
            try {
                clientConfig = new XmlClientConfigBuilder(configResourcePath).build();
            } catch (IOException e) {
                throw new HazelcastException("Could not load client configuration: " + configResourcePath, e);
            }
        } else {
            clientConfig = new ClientConfig();
        }
        if (clientClusterName != null) {
            clientConfig.setClusterName(clientClusterName);
        }
        if (address != null) {
            clientConfig.getNetworkConfig().addAddress(address);
        }

        clientConfig.getNetworkConfig().setSmartRouting(true);
        clientConfig.getNetworkConfig().setRedoOperation(true);

        // Try to connect a cluster with intervals starting with 2 sec and multiplied by 1.5
        // at each step. When the last waiting interval time exceeds 35 seconds, it fails.
        // This corresponds to 8 trials in total.
        clientConfig.getConnectionStrategyConfig().getConnectionRetryConfig().setInitialBackoffMillis(INITIAL_BACKOFF_MS);
        clientConfig.getConnectionStrategyConfig().getConnectionRetryConfig().setMaxBackoffMillis(MAX_BACKOFF_MS);
        clientConfig.getConnectionStrategyConfig().getConnectionRetryConfig().setMultiplier(BACKOFF_MULTIPLIER);
    }

    @Override
    public HazelcastInstance loadInstance() throws CacheException {
        if (instanceName != null) {
            client = HazelcastClient.getHazelcastClientByName(instanceName);
            if (client == null) {
                throw new CacheException("No client with name [" + instanceName + "] could be found.");
            }
        } else {
            client = HazelcastClient.newHazelcastClient(clientConfig);
        }
        return client;
    }

    @Override
    public void unloadInstance() throws CacheException {
        if (client == null) {
            return;
        }

        try {
            client.getLifecycleService().shutdown();
            client = null;
        } catch (Exception e) {
            throw new CacheException(e);
        }
    }
}
