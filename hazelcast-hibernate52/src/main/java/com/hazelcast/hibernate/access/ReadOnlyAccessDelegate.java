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

import com.hazelcast.hibernate.region.HazelcastRegion;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.access.SoftLock;

import java.util.Properties;

/**
 * Guarantees that view is read-only and no updates can be made
 *
 * @param <T> implementation type of HazelcastRegion
 */
public class ReadOnlyAccessDelegate<T extends HazelcastRegion> extends NonStrictReadWriteAccessDelegate<T> {

    public ReadOnlyAccessDelegate(T hazelcastRegion, final Properties props) {
        super(hazelcastRegion, props);
    }

    @Override
    public boolean afterInsert(final Object key, final Object value, final Object version) throws CacheException {
        return cache.insert(key, value, version);
    }

    /**
     *
     * @throws UnsupportedOperationException can't update readonly object
     */
    @Override
    public boolean afterUpdate(final Object key, final Object value, final Object currentVersion,
                               final Object previousVersion, final SoftLock lock) throws CacheException {
        log.finest("Illegal attempt to update item cached as read-only [" + key + "]");
        throw new UnsupportedOperationException("Can't write to a readonly object");
    }

    /**
     * {@inheritDoc}
     * <p/>
     * This cache is asynchronous hence a no-op
     */
    @Override
    public boolean insert(final Object key, final Object value, final Object version) throws CacheException {
        return false;
    }

    @Override
    public SoftLock lockItem(final Object key, final Object version) throws CacheException {
        return null;
    }

    @Override
    public SoftLock lockRegion() throws CacheException {
        return null;
    }

    @Override
    public void removeAll() throws CacheException {
        cache.clear();
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Should be a no-op since this cache is read-only
     */
    @Override
    public void unlockItem(final Object key, final SoftLock lock) throws CacheException {
        /*
         * To err on the safe side though, follow ReadOnlyEhcacheEntityRegionAccessStrategy which nevertheless evicts
         * the key.
         */
        evict(key);
    }

    /**
     * This will issue a log warning stating that an attempt was made to unlock a read-only cache region.
     */
    @Override
    public void unlockRegion(final SoftLock lock) throws CacheException {
        cache.clear();
    }

    /**
     *
     * @throws UnsupportedOperationException can't update readonly object
     */
    @Override
    public boolean update(final Object key, final Object value, final Object currentVersion,
                          final Object previousVersion) throws CacheException {
        log.finest("Illegal attempt to update item cached as read-only [" + key + "]");
        throw new UnsupportedOperationException("Can't update readonly object");
    }
}
