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

package com.hazelcast.hibernate.local;

import com.hazelcast.config.MapConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.topic.ITopic;
import com.hazelcast.topic.Message;
import com.hazelcast.topic.MessageListener;
import com.hazelcast.hibernate.CacheEnvironment;
import com.hazelcast.hibernate.HazelcastTimestamper;
import com.hazelcast.hibernate.RegionCache;
import com.hazelcast.hibernate.serialization.Expirable;
import com.hazelcast.hibernate.serialization.ExpiryMarker;
import com.hazelcast.hibernate.serialization.Value;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.internal.util.Clock;
import com.hazelcast.internal.util.EmptyStatement;
import org.hibernate.cache.cfg.spi.CollectionDataCachingConfig;
import org.hibernate.cache.cfg.spi.DomainDataRegionConfig;
import org.hibernate.cache.cfg.spi.EntityDataCachingConfig;
import org.hibernate.cache.spi.RegionFactory;
import org.hibernate.cache.spi.access.SoftLock;
import org.hibernate.cache.spi.support.AbstractReadWriteAccess;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Local only {@link RegionCache} implementation based on a topic to distribute cache updates.
 */
public class LocalRegionCache implements RegionCache {

    private static final long SEC_TO_MS = 1000L;
    private static final int MAX_SIZE = 100000;
    private static final float BASE_EVICTION_RATE = 0.2F;

    protected final ConcurrentMap<Object, Expirable> cache;

    private final HazelcastInstance hazelcastInstance;
    private final ILogger log = Logger.getLogger(getClass());
    private final String name;
    private final RegionFactory regionFactory;
    private final ITopic<Object> topic;
    private final Comparator versionComparator;

    private MapConfig config;

    /**
     * @param regionFactory     the region factory
     * @param name              the name for this region cache, which is also used to retrieve configuration/topic
     * @param hazelcastInstance the {@code HazelcastInstance} to which this region cache belongs, used to retrieve
     *                          configuration and to lookup an {@link ITopic} to register a {@link MessageListener}
     *                          with (optional)
     * @param regionConfig      the region configuration
     */
    public LocalRegionCache(final RegionFactory regionFactory, final String name,
                            final HazelcastInstance hazelcastInstance, final DomainDataRegionConfig regionConfig) {
        this(regionFactory, name, hazelcastInstance, regionConfig, true);
    }

    /**
     * @param regionFactory     the region factory
     * @param name              the name for this region cache, which is also used to retrieve configuration/topic
     * @param hazelcastInstance the {@code HazelcastInstance} to which this region cache belongs, used to retrieve
     *                          configuration and to lookup an {@link ITopic} to register a {@link MessageListener}
     *                          with if {@code withTopic} is {@code true} (optional)
     * @param regionConfig      the region configuration
     * @param withTopic         {@code true} to register a {@link MessageListener} with the {@link ITopic} whose name
     *                          matches this region cache <i>if</i> a {@code HazelcastInstance} was provided to look
     *                          up the topic; otherwise, {@code false} not to register a listener even if an instance
     *                          was provided
     */
    public LocalRegionCache(final RegionFactory regionFactory, final String name,
                            final HazelcastInstance hazelcastInstance, final DomainDataRegionConfig regionConfig,
                            final boolean withTopic) {
        this.hazelcastInstance = hazelcastInstance;
        this.name = name;
        this.regionFactory = regionFactory;

        try {
            config = hazelcastInstance == null ? null : hazelcastInstance.getConfig().findMapConfig(name);
        } catch (UnsupportedOperationException ignored) {
            EmptyStatement.ignore(ignored);
        }
        cache = new ConcurrentHashMap<>();

        if (withTopic && hazelcastInstance != null) {
            topic = hazelcastInstance.getTopic(name);
            topic.addMessageListener(createMessageListener());
        } else {
            topic = null;
        }

        versionComparator = findVersionComparator(regionConfig);
    }

    @Override
    public void afterUpdate(final Object key, final Object newValue, final Object newVersion) {
        maybeNotifyTopic(key, newValue, newVersion);
    }

    @Override
    public boolean contains(final Object key) {
        return cache.containsKey(key);
    }

    @Override
    public void evictData() {
        cache.clear();
        maybeNotifyTopic(null, null, null);
    }

    @Override
    public void evictData(final Object key) {
        final Expirable value = cache.remove(key);
        maybeNotifyTopic(key, null, (value == null) ? null : value.getVersion());
    }

    @Override
    public Object get(final Object key, final long txTimestamp) {
        final Expirable value = cache.get(key);
        return value == null ? null : value.getValue(txTimestamp);
    }

    @Override
    public long getElementCountInMemory() {
        return cache.size();
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
        return 0;
    }

    @Override
    public boolean put(final Object key, final Object value, final long txTimestamp, final Object version) {
        // The calling code has already done the work of checking if any existing cached entry is replaceable.
        final Value newValue = new Value(version, nextTimestamp(), value);
        cache.put(key, newValue);
        return true;
    }

    @Override
    public void unlockItem(final Object key, final SoftLock lock) {
        maybeNotifyTopic(key, null, null);
    }

    public long nextTimestamp() {
        return hazelcastInstance == null ? Clock.currentTimeMillis()
                : HazelcastTimestamper.nextTimestamp(hazelcastInstance);
    }

    protected Object createMessage(final Object key, final Object value, final Object currentVersion) {
        return new Invalidation(key, currentVersion);
    }

    @SuppressWarnings("Duplicates")
    protected void maybeInvalidate(final Object messageObject) {
        final Invalidation invalidation = (Invalidation) messageObject;
        final Object key = invalidation.getKey();
        if (key == null) {
            // Invalidate the entire region cache.
            cache.clear();
        } else if (versionComparator == null) {
            // For an unversioned entity or collection we can only invalidate the entry.
            cache.remove(key);
        } else {
            // For versioned entities we can avoid the invalidation if both we and the remote node know the version,
            // AND our version is definitely equal or higher.  Otherwise, we have to just invalidate our entry.
            final Expirable value = cache.get(key);
            if (value != null) {
                maybeInvalidateVersionedEntity(key, value, invalidation.getVersion());
            }
        }
    }

    @SuppressWarnings("Duplicates")
    void cleanup() {
        final int maxSize;
        final long timeToLive;
        if (config != null) {
            maxSize = config.getEvictionConfig().getSize();
            timeToLive = config.getTimeToLiveSeconds() * SEC_TO_MS;
        } else {
            maxSize = MAX_SIZE;
            timeToLive = CacheEnvironment.getDefaultCacheTimeoutInMillis();
        }

        final boolean limitSize = maxSize > 0 && maxSize != Integer.MAX_VALUE;
        if (limitSize || timeToLive > 0) {
            final List<EvictionEntry> entries = searchEvictableEntries(timeToLive, limitSize);
            final int diff = cache.size() - maxSize;
            final int evictionRate = calculateEvictionRate(diff, maxSize);
            if (evictionRate > 0 && entries != null) {
                evictEntries(entries, evictionRate);
            }
        }
    }

    void maybeNotifyTopic(final Object key, final Object value, final Object version) {
        if (topic != null) {
            topic.publish(createMessage(key, value, version));
        }
    }

    private int calculateEvictionRate(final int diff, final int maxSize) {
        return diff >= 0 ? (diff + (int) (maxSize * BASE_EVICTION_RATE)) : 0;
    }

    private MessageListener<Object> createMessageListener() {
        return new MessageListener<Object>() {

            @Override
            public void onMessage(final Message<Object> message) {
                maybeInvalidate(message.getMessageObject());
            }
        };
    }

    private void evictEntries(final List<EvictionEntry> entries, final int evictionRate) {
        // Only sort the entries if we're going to evict some
        entries.sort(null);
        int removed = 0;
        for (final EvictionEntry entry : entries) {
            if (cache.remove(entry.key, entry.value) && ++removed == evictionRate) {
                break;
            }
        }
    }

    private Comparator findVersionComparator(final DomainDataRegionConfig regionConfig) {
        if (regionConfig == null) {
            return null;
        }
        for (final EntityDataCachingConfig entityConfig : regionConfig.getEntityCaching()) {
            if (entityConfig.isVersioned()) {
                try {
                    return entityConfig.getVersionComparatorAccess().get();
                } catch (Throwable throwable) {
                    log.warning("Unable to get version comparator", throwable);
                    return null;
                }
            }
        }
        for (final CollectionDataCachingConfig collectionConfig : regionConfig.getCollectionCaching()) {
            if (collectionConfig.isVersioned()) {
                return collectionConfig.getOwnerVersionComparator();
            }
        }
        return null;
    }

    @SuppressWarnings("Duplicates")
    private void maybeInvalidateVersionedEntity(final Object key, final Expirable value, final Object newVersion) {
        if (newVersion == null) {
            // This invalidation was for an entity with unknown version.  Just invalidate the entry
            // unconditionally.
            cache.remove(key);
        } else {
            // Invalidate our entry only if it was of a lower version (we can determine this by checking if the cached
            // item is writeable).
            final AbstractReadWriteAccess.Lockable cachedItem = (AbstractReadWriteAccess.Lockable) value.getValue();
            if (cachedItem.isWriteable(nextTimestamp(), newVersion, versionComparator)) {
                cache.remove(key, value);
            }
        }
    }

    @SuppressWarnings("Duplicates")
    private List<EvictionEntry> searchEvictableEntries(final long timeToLive, final boolean limitSize) {
        List<EvictionEntry> entries = null;
        final Iterator<Entry<Object, Expirable>> iter = cache.entrySet().iterator();
        final long now = nextTimestamp();
        while (iter.hasNext()) {
            final Entry<Object, Expirable> e = iter.next();
            final Object k = e.getKey();
            final Expirable expirable = e.getValue();
            if (expirable instanceof ExpiryMarker) {
                continue;
            }
            final Value v = (Value) expirable;
            if (timeToLive > 0 && v.getTimestamp() + timeToLive < now) {
                iter.remove();
            } else if (limitSize) {
                if (entries == null) {
                    // Use a List rather than a Set for correctness. Using a Set, especially a TreeSet
                    // based on EvictionEntry.compareTo, causes evictions to be processed incorrectly
                    // when two or more entries in the map have the same timestamp. In such a case, the
                    // _first_ entry at a given timestamp is the only one that can be evicted because
                    // TreeSet does not add "equivalent" entries. A second benefit of using a List is
                    // that the cost of sorting the entries is not incurred if eviction isn't performed
                    entries = new ArrayList<>(cache.size());
                }
                entries.add(new EvictionEntry(k, v));
            }
        }
        return entries;
    }

    /**
     * Inner class that instances represent an entry marked for eviction
     */
    private static final class EvictionEntry implements Comparable<EvictionEntry> {
        final Object key;
        final Value value;

        private EvictionEntry(final Object key, final Value value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public int compareTo(final EvictionEntry o) {
            final long thisVal = this.value.getTimestamp();
            final long anotherVal = o.value.getTimestamp();
            return Long.compare(thisVal, anotherVal);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            EvictionEntry that = (EvictionEntry) o;

            return (key == null ? that.key == null : key.equals(that.key))
                    && (value == null ? that.value == null : value.equals(that.value));
        }

        @Override
        public int hashCode() {
            return key == null ? 0 : key.hashCode();
        }
    }
}
