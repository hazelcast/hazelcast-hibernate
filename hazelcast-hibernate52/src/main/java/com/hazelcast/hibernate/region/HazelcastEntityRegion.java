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
import com.hazelcast.hibernate.access.NonStrictReadWriteAccessDelegate;
import com.hazelcast.hibernate.access.ReadOnlyAccessDelegate;
import com.hazelcast.hibernate.access.ReadWriteAccessDelegate;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.CacheDataDescription;
import org.hibernate.cache.spi.EntityRegion;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cache.spi.access.EntityRegionAccessStrategy;

import java.util.Properties;

/**
 * An entity region implementation based upon Hazelcast IMap with basic concurrency / transactional support
 * by supplying EntityRegionAccessStrategy
 *
 * @param <Cache> implementation type of RegionCache
 */
public final class HazelcastEntityRegion<Cache extends RegionCache>
        extends AbstractTransactionalDataRegion<Cache> implements EntityRegion {

    public HazelcastEntityRegion(final HazelcastInstance instance,
                                 final String regionName, final Properties props,
                                 final CacheDataDescription metadata, final Cache cache) {
        super(instance, regionName, props, metadata, cache);
    }

    @Override
    public EntityRegionAccessStrategy buildAccessStrategy(final AccessType accessType) throws CacheException {
        if (AccessType.READ_ONLY.equals(accessType)) {
            return new EntityRegionAccessStrategyAdapter(
                    new ReadOnlyAccessDelegate<HazelcastEntityRegion>(this, props));
        }
        if (AccessType.NONSTRICT_READ_WRITE.equals(accessType)) {
            return new EntityRegionAccessStrategyAdapter(
                    new NonStrictReadWriteAccessDelegate<HazelcastEntityRegion>(this, props));
        }
        if (AccessType.READ_WRITE.equals(accessType)) {
            return new EntityRegionAccessStrategyAdapter(
                    new ReadWriteAccessDelegate<HazelcastEntityRegion>(this, props));
        }
        throw new CacheException("AccessType \"" + accessType + "\" is not currently supported by Hazelcast.");
    }

}
