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
import com.hazelcast.hibernate.local.LocalRegionCache;
import com.hazelcast.hibernate.local.TimestampsRegionCache;
import com.hazelcast.hibernate.region.HazelcastCollectionRegion;
import com.hazelcast.hibernate.region.HazelcastEntityRegion;
import com.hazelcast.hibernate.region.HazelcastNaturalIdRegion;
import com.hazelcast.hibernate.region.HazelcastTimestampsRegion;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.CacheDataDescription;
import org.hibernate.cache.spi.CollectionRegion;
import org.hibernate.cache.spi.EntityRegion;
import org.hibernate.cache.spi.NaturalIdRegion;
import org.hibernate.cache.spi.RegionFactory;
import org.hibernate.cache.spi.TimestampsRegion;

import java.util.Properties;

/**
 * Simple RegionFactory implementation to return Hazelcast based local Region implementations
 */
public class HazelcastLocalCacheRegionFactory extends AbstractHazelcastCacheRegionFactory implements RegionFactory {

    private static final int serialVersionUID = 1;

    public HazelcastLocalCacheRegionFactory() {
    }

    public HazelcastLocalCacheRegionFactory(final HazelcastInstance instance) {
        super(instance);
    }

    public HazelcastLocalCacheRegionFactory(final Properties properties) {
        super(properties);
    }

    @Override
    public CollectionRegion buildCollectionRegion(final String regionName, final Properties properties,
                                                  final CacheDataDescription metadata) throws CacheException {
        final HazelcastCollectionRegion<LocalRegionCache> region = new HazelcastCollectionRegion<>(instance,
          regionName, properties, metadata, new LocalRegionCache(regionName, instance, metadata));
        cleanupService.registerCache(region.getCache());
        return region;
    }

    @Override
    public EntityRegion buildEntityRegion(final String regionName, final Properties properties,
                                          final CacheDataDescription metadata) throws CacheException {
        final HazelcastEntityRegion<LocalRegionCache> region = new HazelcastEntityRegion<>(instance,
          regionName, properties, metadata, new LocalRegionCache(regionName, instance, metadata));
        cleanupService.registerCache(region.getCache());
        return region;
    }

    @Override
    public NaturalIdRegion buildNaturalIdRegion(final String regionName, final Properties properties,
                                                final CacheDataDescription metadata) throws CacheException {
        final HazelcastNaturalIdRegion<LocalRegionCache> region = new HazelcastNaturalIdRegion<>(
          instance, regionName, properties, metadata, new LocalRegionCache(regionName, instance, metadata));
        cleanupService.registerCache(region.getCache());

        return region;
    }

    @Override
    public TimestampsRegion buildTimestampsRegion(final String regionName, final Properties properties)
            throws CacheException {
        return new HazelcastTimestampsRegion<LocalRegionCache>(instance, regionName, properties,
                new TimestampsRegionCache(regionName, instance));
    }
}
