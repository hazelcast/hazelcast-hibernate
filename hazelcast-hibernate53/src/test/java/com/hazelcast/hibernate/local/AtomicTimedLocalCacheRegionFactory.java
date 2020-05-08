package com.hazelcast.hibernate.local;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.hibernate.HazelcastLocalCacheRegionFactory;
import org.hibernate.cache.spi.CacheKeysFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * This class just extends HazelcastLocalCacheRegionFactory to ensure that consecutive timestamps are
 * different and in an increasing manner. Cluster#getClusterTime() may fall short to provide different
 * timestamps when it is called repeatedly and in such a case test results become inconsistent.
 */
public class AtomicTimedLocalCacheRegionFactory extends HazelcastLocalCacheRegionFactory {

    private final AtomicLong epoch = new AtomicLong(1);

    public AtomicTimedLocalCacheRegionFactory() {
        super();
    }

    public AtomicTimedLocalCacheRegionFactory(final CacheKeysFactory cacheKeysFactory) {
        super(cacheKeysFactory);
    }

    public AtomicTimedLocalCacheRegionFactory(final HazelcastInstance instance) {
        super(instance);
    }

    @Override
    public long nextTimestamp() {
        return epoch.getAndIncrement();
    }
}
