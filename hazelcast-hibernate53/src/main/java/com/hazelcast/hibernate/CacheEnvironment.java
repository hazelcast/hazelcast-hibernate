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

package com.hazelcast.hibernate;

import com.hazelcast.logging.Logger;
import org.hibernate.cfg.Environment;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.internal.util.config.ConfigurationException;
import org.hibernate.internal.util.config.ConfigurationHelper;

import java.time.Duration;
import java.util.Map;
import java.util.Properties;

import static java.lang.String.format;
import static org.hibernate.internal.util.config.ConfigurationHelper.getString;

/**
 * This class is used to help in setup the internal caches. It searches for configuration files
 * and contains all property names for hibernate based configuration properties.
 */
public final class CacheEnvironment {

    /**
     * Legacy property to configure the path of the hazelcast.xml or hazelcast-client.xml configuration files
     */
    @Deprecated
    public static final String CONFIG_FILE_PATH_LEGACY = Environment.CACHE_PROVIDER_CONFIG;

    /**
     * Property to configure the path of the hazelcast.xml or hazelcast-client.xml configuration files
     */
    public static final String CONFIG_FILE_PATH = "hibernate.cache.hazelcast.configuration_file_path";

    /**
     * Property to configure weather a Hazelcast client or node will be used for connection to the cluster
     */
    public static final String USE_NATIVE_CLIENT = "hibernate.cache.hazelcast.use_native_client";

    /**
     * Property to configure the address for the Hazelcast client to connect to
     */
    public static final String NATIVE_CLIENT_ADDRESS = "hibernate.cache.hazelcast.native_client_address";

    /**
     * Property to configure Hazelcast client cluster name
     */
    public static final String NATIVE_CLIENT_CLUSTER_NAME = "hibernate.cache.hazelcast.native_client_cluster_name";

    /**
     * Property to configure Hazelcast client instance name
     */
    public static final String NATIVE_CLIENT_INSTANCE_NAME = "hibernate.cache.hazelcast.native_client_instance_name";

    /**
     * Property to configure if the HazelcastInstance should going to shutdown when the RegionFactory is being stopped
     */
    public static final String SHUTDOWN_ON_STOP = "hibernate.cache.hazelcast.shutdown_on_session_factory_close";

    /**
     * Property to enable fallback on datasource if Hazelcast cluster is not available
     */
    public static final String FALLBACK = "hibernate.cache.hazelcast.fallback";

    /**
     * Property to configure the IMDG cluster connection timeout
     */
    public static final String CLUSTER_TIMEOUT = "hibernate.cache.hazelcast.cluster_timeout";

    /**
     * Property to configure the IMDG cluster connection initial backoff
     */
    public static final String INITIAL_BACKOFF_MS = "hibernate.cache.hazelcast.initial_backoff";

    /**
     * Property to configure the IMDG cluster connection max backoff
     */
    public static final String MAX_BACKOFF_MS = "hibernate.cache.hazelcast.max_backoff";

    /**
     * Property to configure the IMDG cluster connection backoff multiplier
     */
    public static final String BACKOFF_MULTIPLIER = "hibernate.cache.hazelcast.backoff_multiplier";

    /**
     * Property to configure the fixed delay in seconds between scheduled cache cleanup jobs
     */
    public static final String CLEANUP_DELAY = "hibernate.cache.hazelcast.cleanup_delay";

    /**
     * Property to configure the Hazelcast instance internal name
     */
    public static final String HAZELCAST_INSTANCE_NAME = "hibernate.cache.hazelcast.instance_name";

    /**
     * Property to configure Hazelcast Shutdown Hook
     */
    public static final String HAZELCAST_SHUTDOWN_HOOK_ENABLED = "hazelcast.shutdownhook.enabled";

    /**
     * Property to configure which {@link com.hazelcast.hibernate.instance.IHazelcastInstanceFactory}
     * that shall be used for creating
     * {@link com.hazelcast.hibernate.instance.IHazelcastInstanceLoader hazelcast instance loaders}.
     */
    public static final String HAZELCAST_FACTORY = "hibernate.cache.hazelcast.factory";

    private static final Duration DEFAULT_CACHE_TIMEOUT = Duration.ofHours(1);

    private static final Duration DEFAULT_MAX_BACKOFF = Duration.ofMillis(35000);

    private static final Duration DEFAULT_INITIAL_BACKOFF = Duration.ofMillis(2000);

    private static final Duration DEFAULT_CACHE_CLEANUP_DELAY = Duration.ofMinutes(1);

    private static final double DEFAULT_BACKOFF_MULTIPLIER = 1.5;


    private CacheEnvironment() {
    }

    public static String getConfigFilePath(final Properties props) {
        String configResourcePath = getString(CacheEnvironment.CONFIG_FILE_PATH_LEGACY, props, null);
        if (StringHelper.isEmpty(configResourcePath)) {
            configResourcePath = getString(CacheEnvironment.CONFIG_FILE_PATH, props, null);
        }
        return configResourcePath;
    }

    public static String getInstanceName(final Properties props) {
        return getString(HAZELCAST_INSTANCE_NAME, props, null);
    }

    public static boolean isNativeClient(final Properties props) {
        return ConfigurationHelper.getBoolean(CacheEnvironment.USE_NATIVE_CLIENT, props, false);
    }

    public static int getDefaultCacheTimeoutInMillis() {
        return (int) DEFAULT_CACHE_TIMEOUT.toMillis();
    }

    public static Duration getCacheCleanup(final Properties props) {
        int delay = -1;
        try {
            delay = ConfigurationHelper.getInt(CLEANUP_DELAY, props, (int) (DEFAULT_CACHE_CLEANUP_DELAY.toMinutes() * 60));
        } catch (Exception e) {
            Logger.getLogger(CacheEnvironment.class).finest(e);
        }
        if (delay < 0) {
            throw new ConfigurationException(format("[%d] is an illegal value for [%s]", delay, CLEANUP_DELAY));
        }
        return Duration.ofSeconds(delay);
    }


    public static boolean shutdownOnStop(final Properties props, final boolean defaultValue) {
        return ConfigurationHelper.getBoolean(CacheEnvironment.SHUTDOWN_ON_STOP, props, defaultValue);
    }

    public static Duration getInitialBackoff(Properties props) {
        int initialBackOff = ConfigurationHelper.getInt(INITIAL_BACKOFF_MS, props, (int) DEFAULT_INITIAL_BACKOFF.toMillis());
        if (initialBackOff <= 0) {
            throw new ConfigurationException("Invalid initial backoff [" + initialBackOff + "]");
        }
        return Duration.ofMillis(initialBackOff);
    }

    public static Duration getMaxBackoff(Properties props) {
        int maxBackoff = ConfigurationHelper.getInt(MAX_BACKOFF_MS, props, (int) DEFAULT_MAX_BACKOFF.toMillis());
        if (maxBackoff <= 0) {
            throw new ConfigurationException("Invalid max backoff [" + maxBackoff + "]");
        }
        return Duration.ofMillis(maxBackoff);
    }

    public static double getBackoffMultiplier(Properties props) {
        double backoffMultiplier = Double.parseDouble(ConfigurationHelper.getString(BACKOFF_MULTIPLIER, props,
          String.valueOf(DEFAULT_BACKOFF_MULTIPLIER)));
        if (backoffMultiplier <= 0) {
            throw new ConfigurationException("Invalid backoff multiplier [" + backoffMultiplier + "]");
        }
        return backoffMultiplier;
    }

    public static Duration getClusterTimeout(final Properties props) {
        int timeoutMillis = ConfigurationHelper.getInt(CLUSTER_TIMEOUT, props, Integer.MAX_VALUE);
        if (timeoutMillis <= 0) {
            throw new ConfigurationException("Invalid cluster timeout [" + timeoutMillis + "]");
        }
        return Duration.ofMillis(timeoutMillis);
    }

    public static boolean getFallback(final Map<String, Object> props) {
        return ConfigurationHelper.getBoolean(CacheEnvironment.FALLBACK, props, true);
    }
}
