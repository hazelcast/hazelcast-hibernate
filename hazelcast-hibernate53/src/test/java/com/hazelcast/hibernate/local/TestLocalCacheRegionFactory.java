package com.hazelcast.hibernate.local;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.hibernate.HazelcastLocalCacheRegionFactory;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.cache.spi.CacheKeysFactory;

import java.util.Map;

/**
 * This class just extends HazelcastLocalCacheRegionFactory to allow using a CleanupService with a shorter delay.
 */
public class TestLocalCacheRegionFactory extends HazelcastLocalCacheRegionFactory {

    // Visible for testing
    public final static long CLEANUP_PERIOD = 12L;

    public TestLocalCacheRegionFactory() {
        super();
    }

    public TestLocalCacheRegionFactory(final CacheKeysFactory cacheKeysFactory) {
        super(cacheKeysFactory);
    }

    public TestLocalCacheRegionFactory(final HazelcastInstance instance) {
        super(instance);
    }

    @Override
    protected void prepareForUse(final SessionFactoryOptions settings, final Map configValues) {
        super.prepareForUse(settings, configValues);
        cleanupService = new CleanupService("TestLocalCacheRegionFactory", CLEANUP_PERIOD);
    }
}
