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
import org.hibernate.cache.spi.NaturalIdRegion;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cache.spi.access.NaturalIdRegionAccessStrategy;

import java.util.Properties;


/**
 * The type Hazelcast natural id region.
 *
 * @param <Cache> NaturalIdRegionCache
 */
public class HazelcastNaturalIdRegion<Cache extends RegionCache>
        extends AbstractTransactionalDataRegion<Cache>
        implements NaturalIdRegion {

    /**
     * Instantiates a new Hazelcast natural id region.
     *
     * @param instance   the instance
     * @param regionName the region name
     * @param props      the props
     * @param metadata   the metadata
     * @param cache      the cache
     */
    public HazelcastNaturalIdRegion(final HazelcastInstance instance, final String regionName,
                                    final Properties props, final CacheDataDescription metadata,
                                    final Cache cache) {
        super(instance, regionName, props, metadata, cache);
    }

    @Override
    public NaturalIdRegionAccessStrategy buildAccessStrategy(final AccessType accessType) throws CacheException {
        if (AccessType.READ_ONLY.equals(accessType)) {
            return new NaturalIdRegionAccessStrategyAdapter(
                    new ReadOnlyAccessDelegate<HazelcastNaturalIdRegion>(this, props));
        }
        if (AccessType.NONSTRICT_READ_WRITE.equals(accessType)) {
            return new NaturalIdRegionAccessStrategyAdapter(
                    new NonStrictReadWriteAccessDelegate<HazelcastNaturalIdRegion>(this, props));
        }
        if (AccessType.READ_WRITE.equals(accessType)) {
            return new NaturalIdRegionAccessStrategyAdapter(
                    new ReadWriteAccessDelegate<HazelcastNaturalIdRegion>(this, props));
        }
        throw new CacheException("AccessType \"" + accessType + "\" is not currently supported by Hazelcast.");
    }
}
