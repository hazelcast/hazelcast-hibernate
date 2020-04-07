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
import com.hazelcast.hibernate.CacheEnvironment;
import com.hazelcast.hibernate.HazelcastTimestamper;
import com.hazelcast.hibernate.RegionCache;
import com.hazelcast.hibernate.serialization.Expirable;
import com.hazelcast.hibernate.serialization.ExpiryMarker;
import com.hazelcast.hibernate.serialization.MarkerWrapper;
import com.hazelcast.hibernate.serialization.Value;
import com.hazelcast.map.IMap;
import org.hibernate.cache.spi.CacheDataDescription;
import org.hibernate.cache.spi.access.SoftLock;

import java.util.Comparator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import static com.hazelcast.hibernate.HazelcastTimestamper.nextTimestamp;

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

    private static final long COMPARISON_VALUE = 500;

    private final String name;
    private final HazelcastInstance hazelcastInstance;
    private final IMap<Object, Expirable> map;
    private final Comparator versionComparator;
    private final int lockTimeout;
    private final long tryLockAndGetTimeout;
    private final AtomicLong markerIdCounter;

    public IMapRegionCache(final String name, final HazelcastInstance hazelcastInstance,
                           final Properties props, final CacheDataDescription metadata) {
        this.name = name;
        this.hazelcastInstance = hazelcastInstance;
        this.versionComparator = metadata != null && metadata.isVersioned() ? metadata.getVersionComparator() : null;
        this.map = hazelcastInstance.getMap(this.name);
        lockTimeout = CacheEnvironment.getLockTimeoutInMillis(props);
        final long maxOperationTimeout = HazelcastTimestamper.getMaxOperationTimeout(hazelcastInstance);
        tryLockAndGetTimeout = Math.min(maxOperationTimeout, COMPARISON_VALUE);
        markerIdCounter = new AtomicLong();
    }

    @Override
    public Object get(final Object key, final long txTimestamp) {
        Expirable entry = map.get(key);
        return entry == null ? null : entry.getValue(txTimestamp);
    }

    @Override
    public boolean insert(final Object key, final Object value, final Object currentVersion) {
        return map.putIfAbsent(key, new Value(currentVersion, nextTimestamp(hazelcastInstance), value)) == null;
    }

    @Override
    public boolean put(final Object key, final Object value, final long txTimestamp, final Object version) {
        // Ideally this would be an entry processor. Unfortunately there is no guarantee that
        // the versionComparator is Serializable.
        //
        // Alternatively this could be implemented using a `map.get` followed by `map.set` wrapped inside a
        // `map.tryLock` block. Unfortunately this implementation was prone to `IllegalMonitorStateException` when
        // the lock was released under heavy load or after network partitions. Hence this implementation now uses
        // a spin loop around atomic operations.
        long timeout = System.currentTimeMillis() + tryLockAndGetTimeout;
        Value newEntry = new Value(version, txTimestamp, value);
        do {
            Expirable currentEntry = map.putIfAbsent(key, newEntry);
            if (currentEntry == null) {
                return true;
            } else if (!currentEntry.isReplaceableBy(txTimestamp, version, versionComparator)) {
                return false;
            } else if (map.replace(key, currentEntry, newEntry)) {
                return true;
            }
        } while (System.currentTimeMillis() < timeout);

        return false;
    }

    @Override
    public boolean update(final Object key, final Object newValue, final Object newVersion, final SoftLock lock) {
        if (lock instanceof MarkerWrapper) {
            final ExpiryMarker unwrappedMarker = ((MarkerWrapper) lock).getMarker();
            return (Boolean) map.executeOnKey(key, new UpdateEntryProcessor(unwrappedMarker, newValue, newVersion,
                    nextMarkerId(), nextTimestamp(hazelcastInstance)));
        } else {
            return false;
        }
    }

    @Override
    public boolean remove(final Object key) {
        return map.remove(key) != null;
    }

    @Override
    public SoftLock tryLock(final Object key, final Object version) {
        long timeout = nextTimestamp(hazelcastInstance) + lockTimeout;
        final ExpiryMarker marker = (ExpiryMarker) map.executeOnKey(key,
                new LockEntryProcessor(nextMarkerId(), timeout, version));
        return new MarkerWrapper(marker);
    }

    @Override
    public void unlock(final Object key, final SoftLock lock) {
        if (lock instanceof MarkerWrapper) {
            final ExpiryMarker unwrappedMarker = ((MarkerWrapper) lock).getMarker();
            map.executeOnKey(key, new UnlockEntryProcessor(unwrappedMarker, nextMarkerId(),
                    nextTimestamp(hazelcastInstance)));
        }
    }

    @Override
    public boolean contains(final Object key) {
        return map.containsKey(key);
    }

    @Override
    public void clear() {
        map.evictAll();
    }

    @Override
    public long size() {
        return map.size();
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
    public Map asMap() {
        return map;
    }

    private String nextMarkerId() {
        return hazelcastInstance.getLocalEndpoint().getUuid().toString() + markerIdCounter.getAndIncrement();
    }

}
