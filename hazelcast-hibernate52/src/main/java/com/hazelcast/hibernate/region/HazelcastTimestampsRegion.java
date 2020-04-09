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

package com.hazelcast.hibernate.region;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.hibernate.RegionCache;
import org.hibernate.cache.spi.TimestampsRegion;

import java.util.Properties;

/**
 * Hazelcast based timestamp using region implementation
 *
 * @param <Cache> implementation type of RegionCache
 */
public class HazelcastTimestampsRegion<Cache extends RegionCache>
        extends AbstractGeneralRegion<Cache> implements TimestampsRegion {

    public HazelcastTimestampsRegion(final HazelcastInstance instance, final String name,
                                     final Properties props, final Cache cache) {
        super(instance, name, props, cache);
    }

}
