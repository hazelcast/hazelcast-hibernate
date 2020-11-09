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
import com.hazelcast.hibernate.serialization.Value;
import com.hazelcast.internal.util.Clock;
import com.hazelcast.internal.util.EmptyStatement;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.topic.ITopic;
import com.hazelcast.topic.MessageListener;
import org.hibernate.cache.cfg.spi.CollectionDataCachingConfig;
import org.hibernate.cache.cfg.spi.DomainDataCachingConfig;
import org.hibernate.cache.cfg.spi.DomainDataRegionConfig;
import org.hibernate.cache.cfg.spi.EntityDataCachingConfig;
import org.hibernate.cache.spi.RegionFactory;
import org.hibernate.cache.spi.access.SoftLock;
import org.hibernate.cache.spi.support.AbstractReadWriteAccess;

import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

/**
 * Local only {@link RegionCache} implementation based on a topic to distribute cache updates.
 */
public class LocalRegionCache implements RegionCache {

    private static final int MAX_SIZE = 100_000;

    protected final ConcurrentMap<Object, Expirable> cache;

    private final HazelcastInstance hazelcastInstance;
    private final ILogger log = Logger.getLogger(getClass());
    private final String name;
    private final RegionFactory regionFactory;
    private final ITopic<Object> topic;
    private final UUID listenerRegistrationId;
    private final Comparator versionComparator;
    private final EvictionConfig evictionConfig;

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
        this(regionFactory, name, hazelcastInstance, regionConfig, withTopic, null);
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
     * @param evictionConfig    provides the parameters which should be used when evicting entries from the cache;
     *                          if null, this will be derived from the Hazelcast {@link MapConfig}; if the MapConfig
     *                          cannot be resolved, this will use defaults.
     */
    public LocalRegionCache(final RegionFactory regionFactory, final String name,
                            final HazelcastInstance hazelcastInstance, final DomainDataRegionConfig regionConfig,
                            final boolean withTopic, final EvictionConfig evictionConfig) {
        this.hazelcastInstance = hazelcastInstance;
        this.name = name;
        this.regionFactory = regionFactory;

        try {
            this.config = hazelcastInstance == null ? null : hazelcastInstance.getConfig().findMapConfig(name);
        } catch (UnsupportedOperationException ignored) {
            EmptyStatement.ignore(ignored);
        }

        if (withTopic && hazelcastInstance != null) {
            this.topic = hazelcastInstance.getTopic(name);
            this.listenerRegistrationId = topic.addMessageListener(createMessageListener());
        } else {
            this.topic = null;
            this.listenerRegistrationId = null;
        }

        this.versionComparator = findVersionComparator(regionConfig).orElse(null);
        this.evictionConfig = evictionConfig == null ? EvictionConfig.create(config) : evictionConfig;

        this.cache = Caffeine.newBuilder()
          .maximumSize(this.evictionConfig.getMaxSize())
          .expireAfterWrite(this.evictionConfig.getTimeToLive().isZero()
            ? Duration.ofDays(365)
            : this.evictionConfig.getTimeToLive())
          .<Object, Expirable>build().asMap();
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

    @Override
    public void destroy() {
        if (topic != null && listenerRegistrationId != null) {
            topic.removeMessageListener(listenerRegistrationId);
        }
    }

    void maybeNotifyTopic(final Object key, final Object value, final Object version) {
        if (topic != null) {
            topic.publish(createMessage(key, value, version));
        }
    }

    private MessageListener<Object> createMessageListener() {
        return message -> maybeInvalidate(message.getMessageObject());
    }

    private Optional<Comparator> findVersionComparator(final DomainDataRegionConfig regionConfig) {
        if (regionConfig == null) {
            return Optional.empty();
        }

        for (EntityDataCachingConfig entityConfig : regionConfig.getEntityCaching()) {
            if (entityConfig.isVersioned()) {
                try {
                    return Optional.ofNullable(entityConfig.getVersionComparatorAccess().get());
                } catch (Throwable throwable) {
                    log.warning("Unable to get version comparator", throwable);
                    return Optional.empty();
                }
            }
        }

        return regionConfig.getCollectionCaching().stream()
          .filter(DomainDataCachingConfig::isVersioned)
          .findFirst()
          .map(CollectionDataCachingConfig::getOwnerVersionComparator);
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

    /**
     * Defines the parameters used when evicting entries from the cache.
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
