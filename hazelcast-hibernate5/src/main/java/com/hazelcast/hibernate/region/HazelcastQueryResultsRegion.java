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
import com.hazelcast.hibernate.local.LocalRegionCache;
import org.hibernate.cache.spi.QueryResultsRegion;

import java.util.Properties;

/**
 * Hazelcast based implementation of a storage region for query results
 */
public class HazelcastQueryResultsRegion extends AbstractGeneralRegion<LocalRegionCache> implements QueryResultsRegion {

    public HazelcastQueryResultsRegion(final HazelcastInstance instance, final String name, final Properties props) {
        // Note: The HazelcastInstance _must_ be passed down here. Otherwise query caches
        // cannot be configured and will always use defaults. However, even though we're
        // passing the HazelcastInstance, we don't want to use an ITopic for invalidation
        // because the timestamps cache can take care of outdated queries
        super(instance, name, props, new LocalRegionCache(name, instance, null, false));
    }
}
