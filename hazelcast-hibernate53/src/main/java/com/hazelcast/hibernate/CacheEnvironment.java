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
import java.util.Properties;

import static java.lang.String.format;

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
     * Property to configure the IMDG cluster connection timeout
     */
    public static final String CLUSTER_TIMEOUT = "hibernate.cache.hazelcast.cluster_timeout";

    /**
     * Property to configure the fixed delay in seconds between scheduled cache cleanup jobs
     */
    public static final String CLEANUP_DELAY = "hibernate.cache.hazelcast.cleanup_delay";

    /**
     * Property to configure the Hazelcast instance internal name
     */
    public static final String HAZELCAST_INSTANCE_NAME = "hibernate.cache.hazelcast.instance_name";

    /**
     * Property to configure explicitly checks the CacheEntry's version while updating RegionCache.
     * If new entry's version is not higher then previous, update will be cancelled.
     */
    public static final String EXPLICIT_VERSION_CHECK = "hibernate.cache.hazelcast.explicit_version_check";

    /**
     * Property to configure the Hazelcast operation timeout
     */
    public static final String HAZELCAST_OPERATION_TIMEOUT = "hazelcast.operation.call.timeout.millis";

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

    // one hour in milliseconds
    private static final int DEFAULT_CACHE_TIMEOUT = (3600 * 1000);

    // one minute in seconds
    private static final int DEFAULT_CACHE_CLEANUP_DELAY = 60;

    private CacheEnvironment() {
    }

    public static String getConfigFilePath(final Properties props) {
        String configResourcePath = ConfigurationHelper.getString(CacheEnvironment.CONFIG_FILE_PATH_LEGACY, props, null);
        if (StringHelper.isEmpty(configResourcePath)) {
            configResourcePath = ConfigurationHelper.getString(CacheEnvironment.CONFIG_FILE_PATH, props, null);
        }
        return configResourcePath;
    }

    public static String getInstanceName(final Properties props) {
        return ConfigurationHelper.getString(HAZELCAST_INSTANCE_NAME, props, null);
    }

    public static boolean isNativeClient(final Properties props) {
        return ConfigurationHelper.getBoolean(CacheEnvironment.USE_NATIVE_CLIENT, props, false);
    }

    public static int getDefaultCacheTimeoutInMillis() {
        return DEFAULT_CACHE_TIMEOUT;
    }

    public static Duration getClusterTimeout(final Properties props) {
        int timeoutMillis = ConfigurationHelper.getInt(CLUSTER_TIMEOUT, props, Integer.MAX_VALUE);
        if (timeoutMillis <= 0) {
            throw new ConfigurationException("Invalid cluster timeout [" + timeoutMillis + "]");
        }
        return Duration.ofMillis(timeoutMillis);
    }

    public static int getCacheCleanupInSeconds(final Properties props) {
        int delay = DEFAULT_CACHE_CLEANUP_DELAY;
        try {
            delay = ConfigurationHelper.getInt(CLEANUP_DELAY, props, DEFAULT_CACHE_CLEANUP_DELAY);
        } catch (Exception e) {
            Logger.getLogger(CacheEnvironment.class).finest(e);
        }
        if (delay < 0) {
            throw new ConfigurationException(format("[%d] is an illegal value for [%s]", delay, CLEANUP_DELAY));
        }
        return delay;
    }


    public static boolean shutdownOnStop(final Properties props, final boolean defaultValue) {
        return ConfigurationHelper.getBoolean(CacheEnvironment.SHUTDOWN_ON_STOP, props, defaultValue);
    }
}
