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
import org.hibernate.cache.CacheException;
import org.hibernate.internal.util.config.ConfigurationHelper;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import static com.hazelcast.client.config.ClientConnectionStrategyConfig.ReconnectMode.ASYNC;
import static com.hazelcast.client.config.ClientConnectionStrategyConfig.ReconnectMode.ON;
import static com.hazelcast.hibernate.CacheEnvironment.NATIVE_CLIENT_ADDRESS;
import static com.hazelcast.hibernate.CacheEnvironment.NATIVE_CLIENT_CLUSTER_NAME;
import static com.hazelcast.hibernate.CacheEnvironment.NATIVE_CLIENT_INSTANCE_NAME;
import static com.hazelcast.hibernate.CacheEnvironment.getBackoffMultiplier;
import static com.hazelcast.hibernate.CacheEnvironment.getClusterTimeout;
import static com.hazelcast.hibernate.CacheEnvironment.getConfigFilePath;
import static com.hazelcast.hibernate.CacheEnvironment.getFallback;
import static com.hazelcast.hibernate.CacheEnvironment.getInitialBackoff;
import static com.hazelcast.hibernate.CacheEnvironment.getMaxBackoff;

/**
 * A factory implementation to build up a {@link HazelcastInstance}
 * implementation using {@link HazelcastClient}.
 */
class HazelcastClientLoader implements IHazelcastInstanceLoader {

    private HazelcastInstance client;
    private ClientConfig clientConfig;
    private String instanceName;

    @Override
    public void configure(final Properties props) {
        instanceName = ConfigurationHelper.getString(NATIVE_CLIENT_INSTANCE_NAME, props, (String) null);
        if (instanceName != null) {
            return;
        }

        String address = ConfigurationHelper.getString(NATIVE_CLIENT_ADDRESS, props, (String) null);
        String clientClusterName = ConfigurationHelper.getString(NATIVE_CLIENT_CLUSTER_NAME, props, (String) null);
        String configResourcePath = getConfigFilePath(props);

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
            String[] addresses = address.trim().split("\\s*,\\s*");
            clientConfig.getNetworkConfig().addAddress(addresses);
        }

        clientConfig.getNetworkConfig()
          .setSmartRouting(true)
          .setRedoOperation(true);

        // By default, try to connect a cluster with intervals starting with 2 sec and multiplied by 1.5
        // at each step with max backoff of 35 seconds
        clientConfig.getConnectionStrategyConfig()
                .setReconnectMode(getFallback(toMap(props)) ? ASYNC : ON)
                .getConnectionRetryConfig()
                .setInitialBackoffMillis((int) getInitialBackoff(props).toMillis())
                .setMaxBackoffMillis((int) getMaxBackoff(props).toMillis())
                .setMultiplier(getBackoffMultiplier(props))
                .setClusterConnectTimeoutMillis(getClusterTimeout(props).toMillis());
    }

    /**
     * Just for testing
     */
    ClientConfig getClientConfig() {
        return clientConfig;
    }

    private Map<String, Object> toMap(Properties props) {
        return props.entrySet().stream().collect(Collectors.toMap(e -> (String) e.getKey(), Map.Entry::getValue));
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
