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
import org.hibernate.cache.spi.CacheDataDescription;
import org.hibernate.cache.spi.TransactionalDataRegion;

import java.util.Properties;

/**
 * Abstract base class of all regions
 *
 * @param <Cache> implementation type of RegionCache
 */
public abstract class AbstractTransactionalDataRegion<Cache extends RegionCache>
        extends AbstractHazelcastRegion<Cache>
        implements TransactionalDataRegion {

    private final CacheDataDescription metadata;
    private final Cache cache;

    protected AbstractTransactionalDataRegion(final HazelcastInstance instance, final String regionName,
                                              final Properties props, final CacheDataDescription metadata,
                                              final Cache cache) {
        super(instance, regionName, props);
        this.metadata = metadata;
        this.cache = cache;
    }

    @Override
    public CacheDataDescription getCacheDataDescription() {
        return metadata;
    }

    @Override
    public boolean isTransactionAware() {
        return false;
    }

    @Override
    public Cache getCache() {
        return cache;
    }
}
