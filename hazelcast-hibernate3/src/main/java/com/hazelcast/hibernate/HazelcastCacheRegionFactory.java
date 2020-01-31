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
import com.hazelcast.hibernate.distributed.IMapRegionCache;
import com.hazelcast.hibernate.region.HazelcastCollectionRegion;
import com.hazelcast.hibernate.region.HazelcastEntityRegion;
import com.hazelcast.hibernate.region.HazelcastTimestampsRegion;
import org.hibernate.cache.CacheDataDescription;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.CollectionRegion;
import org.hibernate.cache.EntityRegion;
import org.hibernate.cache.RegionFactory;
import org.hibernate.cache.TimestampsRegion;

import java.util.Properties;

/**
 * Simple RegionFactory implementation to return Hazelcast based Region implementations
 */
public class HazelcastCacheRegionFactory extends AbstractHazelcastCacheRegionFactory implements RegionFactory {

    @SuppressWarnings("unused")
    public HazelcastCacheRegionFactory() {
    }

    @SuppressWarnings("unused")
    public HazelcastCacheRegionFactory(final HazelcastInstance instance) {
        super(instance);
    }

    @SuppressWarnings("unused")
    public HazelcastCacheRegionFactory(final Properties properties) {
        super(properties);
    }

    @Override
    public CollectionRegion buildCollectionRegion(final String regionName, final Properties properties,
                                                  final CacheDataDescription metadata) throws CacheException {
        return new HazelcastCollectionRegion<IMapRegionCache>(instance, regionName, properties, metadata,
                new IMapRegionCache(regionName, instance, properties, metadata));
    }

    @Override
    public EntityRegion buildEntityRegion(final String regionName, final Properties properties,
                                          final CacheDataDescription metadata) throws CacheException {
        return new HazelcastEntityRegion<IMapRegionCache>(instance, regionName, properties, metadata,
                new IMapRegionCache(regionName, instance, properties, metadata));
    }

    @Override
    public TimestampsRegion buildTimestampsRegion(final String regionName, final Properties properties)
            throws CacheException {
        return new HazelcastTimestampsRegion<IMapRegionCache>(instance, regionName, properties,
                new IMapRegionCache(regionName, instance, properties, null));
    }
}
