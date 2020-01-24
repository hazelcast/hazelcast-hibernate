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

package com.hazelcast.hibernate.distributed;

import com.hazelcast.core.EntryView;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.hibernate.RegionCache;
import com.hazelcast.hibernate.serialization.Expirable;
import com.hazelcast.hibernate.serialization.Value;
import org.hibernate.cache.spi.RegionFactory;
import org.hibernate.cache.spi.access.SoftLock;

/**
 * A {@link RegionCache} implementation based on the underlying IMap
 * <p/>
 * Note, IMap locks are intentionally not used in this class. Hibernate region caches make use of a concept
 * called soft-locking which has the following properties:
 * <ul>
 *     <li>Multiple transactions can soft-lock an entry concurrently</li>
 *     <li>While an entry is soft-locked, the value of the cache entry is always {@code null}</li>
 *     <li>An entry is unlocked from a soft-lock when all transactions complete</li>
 *     <li>An entry is unlocked if it reaches the configured lock timeout</li>
 * </ul>
 * These requirements are incompatible with IMap locks
 */
public class IMapRegionCache implements RegionCache {

    private final IMap<Object, Expirable> map;
    private final String name;
    private final RegionFactory regionFactory;

    public IMapRegionCache(final RegionFactory regionFactory, final String name,
                           final HazelcastInstance hazelcastInstance) {
        this.name = name;
        this.regionFactory = regionFactory;

        this.map = hazelcastInstance.getMap(this.name);
    }

    @Override
    public void afterUpdate(final Object key, final Object newValue, final Object newVersion) {
        // no-op
    }

    @Override
    public boolean contains(final Object key) {
        return map.containsKey(key);
    }

    @Override
    public void evictData() {
        map.evictAll();
    }

    @Override
    public void evictData(final Object key) {
        map.remove(key);
    }

    @Override
    public Object get(final Object key, final long txTimestamp) {
        final Expirable entry = map.get(key);
        return entry == null ? null : entry.getValue(txTimestamp);
    }

    @Override
    public long getElementCountInMemory() {
        return map.size();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public RegionFactory getRegionFactory() {
        return regionFactory;
    }

    @Override
    public long getSizeInMemory() {
        long size = 0;
        for (final Object key : map.keySet()) {
            final EntryView entry = map.getEntryView(key);
            if (entry != null) {
                size += entry.getCost();
            }
        }
        return size;
    }

    @Override
    public boolean put(final Object key, final Object value, final long txTimestamp, final Object version) {
        final Value newValue = new Value(version, txTimestamp, value);
        map.put(key, newValue);
        return true;
    }

    @Override
    public void unlockItem(final Object key, final SoftLock lock) {
        // no-op
    }
}
