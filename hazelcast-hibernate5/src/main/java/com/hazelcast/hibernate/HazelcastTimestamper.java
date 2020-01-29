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

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.logging.Logger;

/**
 * Helper class to create timestamps and calculate timeouts based on either Hazelcast
 * configuration of by requesting values on the cluster.
 */
public final class HazelcastTimestamper {

    private static final int SEC_TO_MS = 1000;

    private HazelcastTimestamper() {
    }

    public static long nextTimestamp(final HazelcastInstance instance) {
        if (instance == null) {
            throw new RuntimeException("No Hazelcast instance!");
        } else if (instance.getCluster() == null) {
            throw new RuntimeException("Hazelcast instance has no cluster!");
        }

        // System time in ms.
        return instance.getCluster().getClusterTime();
    }

    public static int getTimeout(final HazelcastInstance instance, final String regionName) {
        try {
            final MapConfig cfg = instance.getConfig().findMapConfig(regionName);
            if (cfg.getTimeToLiveSeconds() > 0) {
                // TTL in ms
                return cfg.getTimeToLiveSeconds() * SEC_TO_MS;
            }
        } catch (UnsupportedOperationException e) {
            // HazelcastInstance is instance of HazelcastClient.
            Logger.getLogger(HazelcastTimestamper.class).finest(e);
        }
        return CacheEnvironment.getDefaultCacheTimeoutInMillis();
    }

    public static long getMaxOperationTimeout(final HazelcastInstance instance) {
        String maxOpTimeoutProp = null;
        try {
            Config config = instance.getConfig();
            maxOpTimeoutProp = config.getProperty(CacheEnvironment.HAZELCAST_OPERATION_TIMEOUT);
        } catch (UnsupportedOperationException e) {
            // HazelcastInstance is instance of HazelcastClient.
            Logger.getLogger(HazelcastTimestamper.class).finest(e);
        }
        if (maxOpTimeoutProp != null) {
            return Long.parseLong(maxOpTimeoutProp);
        }
        return Long.MAX_VALUE;
    }
}
