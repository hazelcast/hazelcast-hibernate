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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.hibernate.CacheEnvironment;
import com.hazelcast.hibernate.HazelcastTimestamper;
import com.hazelcast.hibernate.RegionCache;
import com.hazelcast.hibernate.serialization.Expirable;
import com.hazelcast.hibernate.serialization.ExpiryMarker;
import com.hazelcast.hibernate.serialization.MarkerWrapper;
import com.hazelcast.hibernate.serialization.Value;
import com.hazelcast.internal.util.Clock;
import com.hazelcast.internal.util.EmptyStatement;
import com.hazelcast.topic.ITopic;
import com.hazelcast.topic.MessageListener;
import org.hibernate.cache.spi.CacheDataDescription;
import org.hibernate.cache.spi.access.SoftLock;

import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Local only {@link com.hazelcast.hibernate.RegionCache} implementation
 * based on a topic to distribute cache updates.
 */
public class LocalRegionCache implements RegionCache {

    private static final int MAX_SIZE = 100000;

    protected final HazelcastInstance hazelcastInstance;
    protected final ITopic<Object> topic;
    protected final UUID listenerRegistrationId;
    protected final MessageListener<Object> messageListener;
    protected final ConcurrentMap<Object, Expirable> cache;
    protected final Comparator versionComparator;
    protected final AtomicLong markerIdCounter;
    protected MapConfig config;
    private final EvictionConfig evictionConfig;

    /**
     * @param name              the name for this region cache, which is also used to retrieve configuration/topic
     * @param hazelcastInstance the {@code HazelcastInstance} to which this region cache belongs, used to retrieve
     *                          configuration and to lookup an {@link ITopic} to register a {@link MessageListener}
     *                          with (optional)
     * @param metadata          metadata describing the cached data, used to compare data versions (optional)
     */
    public LocalRegionCache(final String name, final HazelcastInstance hazelcastInstance,
                            final CacheDataDescription metadata) {
        this(name, hazelcastInstance, metadata, true);
    }

    /**
     * @param name              the name for this region cache, which is also used to retrieve configuration/topic
     * @param hazelcastInstance the {@code HazelcastInstance} to which this region cache belongs, used to retrieve
     *                          configuration and to lookup an {@link ITopic} to register a {@link MessageListener}
     *                          with if {@code withTopic} is {@code true} (optional)
     * @param metadata          metadata describing the cached data, used to compare data versions (optional)
     * @param withTopic         {@code true} to register a {@link MessageListener} with the {@link ITopic} whose name
     *                          matches this region cache <i>if</i> a {@code HazelcastInstance} was provided to look
     *                          up the topic; otherwise, {@code false} not to register a listener even if an instance
     *                          was provided
     */
    public LocalRegionCache(final String name, final HazelcastInstance hazelcastInstance,
                            final CacheDataDescription metadata, final boolean withTopic) {
        this(name, hazelcastInstance, metadata, withTopic, null);
    }

    /**
     * @param name              the name for this region cache, which is also used to retrieve configuration/topic
     * @param hazelcastInstance the {@code HazelcastInstance} to which this region cache belongs, used to retrieve
     *                          configuration and to lookup an {@link ITopic} to register a {@link MessageListener}
     *                          with if {@code withTopic} is {@code true} (optional)
     * @param metadata          metadata describing the cached data, used to compare data versions (optional)
     * @param withTopic         {@code true} to register a {@link MessageListener} with the {@link ITopic} whose name
     *                          matches this region cache <i>if</i> a {@code HazelcastInstance} was provided to look
     *                          up the topic; otherwise, {@code false} not to register a listener even if an instance
     *                          was provided
     * @param evictionConfig    provides the parameters which should be used when evicting entries from the cache;
     *                          if null, this will be derived from the Hazelcast {@link MapConfig}; if the MapConfig
     *                          cannot be resolved, this will use defaults.
     */
    public LocalRegionCache(final String name, final HazelcastInstance hazelcastInstance,
                            final CacheDataDescription metadata, final boolean withTopic,
                            final EvictionConfig evictionConfig) {
        this.hazelcastInstance = hazelcastInstance;
        try {
            config = hazelcastInstance != null ? hazelcastInstance.getConfig().findMapConfig(name) : null;
        } catch (UnsupportedOperationException ignored) {
            EmptyStatement.ignore(ignored);
        }
        versionComparator = metadata != null && metadata.isVersioned() ? metadata.getVersionComparator() : null;
        markerIdCounter = new AtomicLong();

        messageListener = createMessageListener();
        if (withTopic && hazelcastInstance != null) {
            topic = hazelcastInstance.getTopic(name);
            listenerRegistrationId = topic.addMessageListener(messageListener);
        } else {
            topic = null;
            listenerRegistrationId = null;
        }
        this.evictionConfig = evictionConfig == null ? EvictionConfig.create(config) : evictionConfig;
        this.cache = Caffeine.newBuilder()
          .maximumSize(this.evictionConfig.getMaxSize())
          .expireAfterWrite(resolveTTL())
          .<Object, Expirable>build().asMap();
    }

    @Override
    public Object get(final Object key, long txTimestamp) {
        final Expirable value = cache.get(key);
        return value == null ? null : value.getValue(txTimestamp);
    }

    @Override
    public boolean insert(final Object key, final Object value, final Object currentVersion) {
        final Value newValue = new Value(currentVersion, nextTimestamp(), value);
        return cache.putIfAbsent(key, newValue) == null;
    }

    @Override
    public boolean put(final Object key, final Object value, final long txTimestamp, final Object version) {
        while (true) {
            Expirable previous = cache.get(key);
            Value newValue = new Value(version, nextTimestamp(), value);
            if (previous == null) {
                if (cache.putIfAbsent(key, newValue) == null) {
                    return true;
                }
            } else if (previous.isReplaceableBy(txTimestamp, version, versionComparator)) {
                if (cache.replace(key, previous, newValue)) {
                    return true;
                }
            } else {
                return false;
            }
        }
    }

    @Override
    public boolean update(final Object key, final Object newValue, final Object newVersion, final SoftLock softLock) {
        boolean updated = false;
        while (true) {
            Expirable original = cache.get(key);
            Expirable revised;
            long timestamp = nextTimestamp();
            if (original == null) {
                // The entry must have expired. it should be safe to update
                revised = new Value(newVersion, timestamp, newValue);
                updated = true;
                if (cache.putIfAbsent(key, revised) == null) {
                    break;
                }
            } else {
                if (softLock instanceof MarkerWrapper) {
                    final ExpiryMarker unwrappedMarker = ((MarkerWrapper) softLock).getMarker();
                    if (original.matches(unwrappedMarker)) {
                        // The lock matches
                        final ExpiryMarker marker = (ExpiryMarker) original;
                        if (marker.isConcurrent()) {
                            revised = marker.expire(timestamp);
                            updated = false;
                        } else {
                            revised = new Value(newVersion, timestamp, newValue);
                            updated = true;
                        }
                        if (cache.replace(key, original, revised)) {
                            break;
                        }
                    } else if (original.getValue() == null) {
                        // It's marked for expiration, leave it as is
                        updated = false;
                        break;
                    } else {
                        // It's a value. Instead of removing it, expire it to prevent stale from in progress
                        // transactions being put in the cache
                        revised = new ExpiryMarker(newVersion, timestamp, nextMarkerId()).expire(timestamp);
                        updated = false;
                        if (cache.replace(key, original, revised)) {
                            break;
                        }
                    }
                } else {
                    break;
                }
            }
        }
        maybeNotifyTopic(key, newValue, newVersion);

        return updated;
    }

    protected void maybeNotifyTopic(final Object key, final Object value, final Object version) {
        if (topic != null) {
            topic.publish(createMessage(key, value, version));
        }
    }

    protected Object createMessage(final Object key, final Object value, final Object currentVersion) {
        return new Invalidation(key, currentVersion);
    }

    protected MessageListener<Object> createMessageListener() {
        return message -> maybeInvalidate(message.getMessageObject());
    }

    @Override
    public boolean remove(final Object key) {
        final Expirable value = cache.remove(key);
        maybeNotifyTopic(key, null, (value == null) ? null : value.getVersion());
        return (value != null);
    }

    @Override
    public SoftLock tryLock(final Object key, final Object version) {
        ExpiryMarker marker;
        String markerId = nextMarkerId();
        while (true) {
            final Expirable original = cache.get(key);
            long timeout = nextTimestamp() + CacheEnvironment.getDefaultCacheTimeoutInMillis();
            if (original == null) {
                marker = new ExpiryMarker(version, timeout, markerId);
                if (cache.putIfAbsent(key, marker) == null) {
                    break;
                }
            } else {
                marker = original.markForExpiration(timeout, markerId);
                if (cache.replace(key, original, marker)) {
                    break;
                }
            }
        }
        return new MarkerWrapper(marker);
    }

    @Override
    public void unlock(final Object key, final SoftLock lock) {
        while (true) {
            final Expirable original = cache.get(key);
            if (original != null) {
                if (!(lock instanceof MarkerWrapper)) {
                    break;
                }
                final ExpiryMarker unwrappedMarker = ((MarkerWrapper) lock).getMarker();
                if (original.matches(unwrappedMarker)) {
                    final Expirable revised = ((ExpiryMarker) original).expire(nextTimestamp());
                    if (cache.replace(key, original, revised)) {
                        break;
                    }
                } else if (original.getValue() != null) {
                    if (cache.remove(key, original)) {
                        break;
                    }
                } else {
                    break;
                }
            } else {
                break;
            }
        }
        maybeNotifyTopic(key, null, null);
    }

    @Override
    public boolean contains(final Object key) {
        return cache.containsKey(key);
    }

    @Override
    public void clear() {
        cache.clear();
        maybeNotifyTopic(null, null, null);
    }

    @Override
    public void destroy() {
        if (topic != null && listenerRegistrationId != null) {
            topic.removeMessageListener(listenerRegistrationId);
        }
    }

    @Override
    public long size() {
        return cache.size();
    }

    @Override
    public long getSizeInMemory() {
        return 0;
    }

    @Override
    public Map asMap() {
        return cache;
    }

    protected void maybeInvalidate(final Object messageObject) {
        Invalidation invalidation = (Invalidation) messageObject;
        Object key = invalidation.getKey();
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

    private void maybeInvalidateVersionedEntity(final Object key, final Expirable value, final Object newVersion) {
        if (newVersion == null) {
            // This invalidation was for an entity with unknown version.  Just invalidate the entry
            // unconditionally.
            cache.remove(key);
        } else {
            // Invalidate our entry only if it was of a lower version.
            Object currentVersion = value.getVersion();
            if (versionComparator.compare(currentVersion, newVersion) < 0) {
                cache.remove(key, value);
            }
        }
    }

    private String nextMarkerId() {
        return Long.toString(markerIdCounter.getAndIncrement());
    }

    protected long nextTimestamp() {
        return hazelcastInstance == null ? Clock.currentTimeMillis()
          : HazelcastTimestamper.nextTimestamp(hazelcastInstance);
    }

    private Duration resolveTTL() {
        // zero is interpreted differently by Hazelcast and Caffeine
        return Math.max(evictionConfig.getTimeToLive().toMillis(), 0) == 0
          ? Duration.ofMillis(Integer.MAX_VALUE)
          : evictionConfig.getTimeToLive();
    }

    /**
     * Generic representation of eviction-related configuration.
     */
    public interface EvictionConfig {
        /**
         * @return the duration for which an item should live in the cache
         */
        Duration getTimeToLive();

        /**
         * @return the maximum number of entries that should live in the cache
         */
        int getMaxSize();

        /**
         * Creates an {@link EvictionConfig} for a given Hazelcast {@link MapConfig}.
         *
         * @param mapConfig the MapConfig to use. If null, defaults will be used.
         */
        static EvictionConfig create(final MapConfig mapConfig) {
            return new EvictionConfig() {
                @Override
                public Duration getTimeToLive() {
                    return mapConfig == null
                      ? Duration.ofMillis(CacheEnvironment.getDefaultCacheTimeoutInMillis())
                      : Duration.ofSeconds(mapConfig.getTimeToLiveSeconds());
                }

                @Override
                public int getMaxSize() {
                    return mapConfig == null
                      ? MAX_SIZE
                      : mapConfig.getEvictionConfig().getSize();
                }
            };
        }
    }
}
