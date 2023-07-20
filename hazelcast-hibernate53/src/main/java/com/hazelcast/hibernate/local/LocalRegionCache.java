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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hazelcast.client.impl.clientside.HazelcastClientProxy;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MaxSizePolicy;
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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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

import static com.hazelcast.memory.MemoryUnit.MEGABYTES;

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
    private FreeHeapBasedCacheEvictor freeHeapBasedCacheEvictor;

    private MapConfig config;

    /**
     * @param regionFactory             the region factory
     * @param name                      the name for this region cache, which is also used to retrieve configuration/topic
     * @param hazelcastInstance         the {@code HazelcastInstance} to which this region cache belongs, used to retrieve
     *                                  configuration and to lookup an {@link ITopic} to register a {@link MessageListener}
     *                                  with if {@code withTopic} is {@code true} (optional)
     * @param regionConfig              the region configuration
     * @param withTopic                 {@code true} to register a {@link MessageListener} with the {@link ITopic} whose name
     *                                  matches this region cache <i>if</i> a {@code HazelcastInstance} was provided to look
     *                                  up the topic; otherwise, {@code false} not to register a listener even if an instance
     *                                  was provided
     * @param evictionConfig            provides the parameters which should be used when evicting entries from the cache;
     *                                  if null, this will be derived from the Hazelcast {@link MapConfig}; if the MapConfig
     *                                  cannot be resolved, this will use defaults.
     * @param freeHeapBasedCacheEvictor performs the free-heap-based eviction
     */
    protected LocalRegionCache(final RegionFactory regionFactory, final String name,
                               final HazelcastInstance hazelcastInstance, final DomainDataRegionConfig regionConfig,
                               final boolean withTopic, final EvictionConfig evictionConfig,
                               final FreeHeapBasedCacheEvictor freeHeapBasedCacheEvictor) {
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

        this.cache = createCache(freeHeapBasedCacheEvictor, name);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private RegionFactory regionFactory;
        private String name;
        private HazelcastInstance hazelcastInstance;
        private DomainDataRegionConfig regionConfig;
        private boolean withTopic;
        private EvictionConfig evictionConfig;
        private FreeHeapBasedCacheEvictor freeHeapBasedCacheEvictor;

        public LocalRegionCache build() {
            return new LocalRegionCache(regionFactory, name,
                    hazelcastInstance, regionConfig,
                    withTopic, evictionConfig,
                    freeHeapBasedCacheEvictor);
        }

        public Builder withRegionFactory(RegionFactory regionFactory) {
            this.regionFactory = regionFactory;
            return this;
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        @SuppressFBWarnings("EI_EXPOSE_REP2")
        public Builder withHazelcastInstance(HazelcastInstance hazelcastInstance) {
            this.hazelcastInstance = hazelcastInstance;
            return this;
        }

        public Builder withRegionConfig(DomainDataRegionConfig regionConfig) {
            this.regionConfig = regionConfig;
            return this;
        }

        public Builder withTopic(boolean withTopic) {
            this.withTopic = withTopic;
            return this;
        }

        public Builder withEvictionConfig(EvictionConfig evictionConfig) {
            this.evictionConfig = evictionConfig;
            return this;
        }

        public Builder withFreeHeapBasedCacheEvictor(FreeHeapBasedCacheEvictor freeHeapBasedCacheEvictor) {
            this.freeHeapBasedCacheEvictor = freeHeapBasedCacheEvictor;
            return this;
        }
    }

    private ConcurrentMap<Object, Expirable> createCache(FreeHeapBasedCacheEvictor freeHeapBasedCacheEvictor, String name) {
        Caffeine<Object, Object> caffeineBuilder = Caffeine.newBuilder()
                .expireAfterWrite(resolveTTL());
        MaxSizePolicy maxSizePolicy = evictionConfig.getMaxSizePolicy();
        Cache<Object, Expirable> caffeineCache = null;
        if (maxSizePolicy == null) {
            maxSizePolicy = MaxSizePolicy.PER_NODE;
        }
        switch (maxSizePolicy) {
            case PER_NODE:
                caffeineBuilder.maximumSize(evictionConfig.getSize());
                break;
            case FREE_HEAP_SIZE:
                this.freeHeapBasedCacheEvictor = freeHeapBasedCacheEvictor;
                assertEvictorPresent(maxSizePolicy);
                enableEviction(caffeineBuilder);
                caffeineCache = caffeineBuilder.build();
                long minimalHeapSizeInMB = MEGABYTES.toBytes(evictionConfig.getSize());
                this.freeHeapBasedCacheEvictor.start(name, caffeineCache, minimalHeapSizeInMB);
                break;
            default:
                throw new IllegalArgumentException(maxSizePolicy + " policy not supported");
        }
        if (caffeineCache == null) {
            caffeineCache = caffeineBuilder.build();
        }
        return caffeineCache.asMap();
    }

    private void enableEviction(Caffeine<Object, Object> caffeineBuilder) {
        //cache has to be size-bound to enable eviction, see caffeineCache.policy().eviction()
        caffeineBuilder.maximumSize(Long.MAX_VALUE);
    }

    private void assertEvictorPresent(MaxSizePolicy maxSizePolicy) {
        if (freeHeapBasedCacheEvictor == null) {
            throw new IllegalStateException(FreeHeapBasedCacheEvictor.class.getSimpleName() + " is required for "
                    + maxSizePolicy + " policy");
        }
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
        return hazelcastInstance == null
          ? Clock.currentTimeMillis()
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
        if (freeHeapBasedCacheEvictor != null) {
            freeHeapBasedCacheEvictor.stop(name);
        }
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
        return message -> {
            // Updates made by current node should have been reflected in its local cache already.
            // Invalidation is only needed if updates came from other node(s).
            if (message.getPublishingMember() == null
                    || hazelcastInstance == null
                    || isClient()
                    || !message.getPublishingMember().equals(hazelcastInstance.getCluster().getLocalMember())) {
                maybeInvalidate(message.getMessageObject());
            }
        };
    }

    private boolean isClient() {
        return hazelcastInstance instanceof HazelcastClientProxy;
    }

    private Optional<Comparator<?>> findVersionComparator(final DomainDataRegionConfig regionConfig) {
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

    private Duration resolveTTL() {
        // zero is interpreted differently by Hazelcast and Caffeine
        return Math.max(evictionConfig.getTimeToLive().toMillis(), 0) == 0
          ? Duration.ofMillis(Integer.MAX_VALUE)
          : evictionConfig.getTimeToLive();
    }

    /**
     * Generic representation of eviction-related configuration
     * <p>
     * See {@link com.hazelcast.config.EvictionConfig}
     */
    public interface EvictionConfig {

        /**
         * @return the maximum number of seconds for each entry to stay in the map.
         */
        Duration getTimeToLive();

        /**
         * Use {@link EvictionConfig#getSize()} instead
         */
        @Deprecated
        default int getMaxSize() {
            return getSize();
        }

        /**
         * Returns the size which is used by the {@link MaxSizePolicy}.
         * <p>
         * The interpretation of the value depends
         * on the configured {@link MaxSizePolicy}.
         *
         * @return the size which is used by the {@link MaxSizePolicy}
         */
        int getSize();

        /**
         * @return the {@link MaxSizePolicy} of this eviction configuration
         */
        MaxSizePolicy getMaxSizePolicy();

        /**
         * Creates an {@link EvictionConfig} for a given Hazelcast {@link MapConfig}.
         *
         * @param mapConfig the MapConfig to use. If null, defaults will be used.
         */
        static EvictionConfig create(final MapConfig mapConfig) {
            return new DefaultEvictionConfig(mapConfig);
        }

        class DefaultEvictionConfig implements EvictionConfig {
            private final MapConfig mapConfig;

            DefaultEvictionConfig(MapConfig mapConfig) {
                this.mapConfig = mapConfig;
            }

            @Override
            public Duration getTimeToLive() {
                return mapConfig == null
                        ? Duration.ofMillis(CacheEnvironment.getDefaultCacheTimeoutInMillis())
                        : Duration.ofSeconds(mapConfig.getTimeToLiveSeconds());
            }

            @Override
            public int getSize() {
                return mapConfig == null
                        ? MAX_SIZE
                        : mapConfig.getEvictionConfig().getSize();
            }

            @Override
            public MaxSizePolicy getMaxSizePolicy() {
                return mapConfig == null
                        ? com.hazelcast.config.MapConfig.DEFAULT_MAX_SIZE_POLICY
                        : mapConfig.getEvictionConfig().getMaxSizePolicy();
            }
        }
    }
}
