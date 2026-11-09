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

import com.hazelcast.core.HazelcastInstance;

/**
 * Helper class to create timestamps and calculate timeouts based on either Hazelcast
 * configuration of by requesting values on the cluster.
 */
public final class HazelcastTimestamper {

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
}
