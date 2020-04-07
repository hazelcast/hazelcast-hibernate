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

package com.hazelcast.hibernate.access;

import com.hazelcast.core.HazelcastException;
import com.hazelcast.hibernate.RegionCache;
import com.hazelcast.hibernate.region.AbstractTransactionalDataRegion;
import com.hazelcast.hibernate.region.HazelcastRegion;
import com.hazelcast.logging.ILogger;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.access.SoftLock;

import java.util.Comparator;
import java.util.Properties;

/**
 * Base implementation for consistency guarantees
 *
 * @param <T> implementation type of HazelcastRegion
 */
public abstract class AbstractAccessDelegate<T extends HazelcastRegion> implements AccessDelegate<T> {

    protected final ILogger log;
    protected final T hazelcastRegion;
    protected final RegionCache cache;
    protected final Comparator<Object> versionComparator;

    protected AbstractAccessDelegate(final T hazelcastRegion, final Properties props) {
        this.hazelcastRegion = hazelcastRegion;
        log = hazelcastRegion.getLogger();
        if (hazelcastRegion instanceof AbstractTransactionalDataRegion) {
            this.versionComparator = ((AbstractTransactionalDataRegion) hazelcastRegion)
                    .getCacheDataDescription().getVersionComparator();
        } else {
            this.versionComparator = null;
        }
        cache = hazelcastRegion.getCache();
    }

    @Override
    public final T getHazelcastRegion() {
        return hazelcastRegion;
    }

    @Override
    public Object get(final Object key, final long txTimestamp) throws CacheException {
        try {
            return cache.get(key, txTimestamp);
        } catch (HazelcastException e) {
            if (log.isFinestEnabled()) {
                log.finest("Could not read from Cache[" + hazelcastRegion.getName() + "]: " + e.getMessage());
            }
            return null;
        }
    }

    @Override
    public boolean putFromLoad(final Object key, final Object value, final long txTimestamp,
                               final Object version) throws CacheException {
        try {
            return cache.put(key, value, txTimestamp, version);
        } catch (HazelcastException e) {
            if (log.isFinestEnabled()) {
                log.finest("Could not put into Cache[" + hazelcastRegion.getName() + "]: " + e.getMessage());
            }
            return false;
        }
    }

    @Override
    public boolean putFromLoad(final Object key, final Object value, final long txTimestamp,
                               final Object version, boolean minimalPuts) throws CacheException {
        return putFromLoad(key, value, txTimestamp, version);
    }

    @Override
    public void evict(final Object key) throws CacheException {
        cache.remove(key);
    }

    @Override
    public void evictAll() throws CacheException {
        cache.clear();
    }

    /**
     * NO-OP
     */
    @Override
    public SoftLock lockRegion() throws CacheException {
        return null;
    }

    @Override
    public void unlockRegion(final SoftLock lock) throws CacheException {
        cache.clear();
    }
}
