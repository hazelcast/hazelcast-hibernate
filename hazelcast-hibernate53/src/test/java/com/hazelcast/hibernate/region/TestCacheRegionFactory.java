package com.hazelcast.hibernate.region;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.hibernate.HazelcastCacheRegionFactory;
import com.hazelcast.hibernate.local.CleanupService;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.cache.spi.CacheKeysFactory;

import java.util.Map;

import static com.hazelcast.hibernate.local.TestLocalCacheRegionFactory.CLEANUP_PERIOD;

/**
 * This class just extends HazelcastCacheRegionFactory to allow using a CleanupService with a shorter delay.
 */
public class TestCacheRegionFactory extends HazelcastCacheRegionFactory {

    public TestCacheRegionFactory() {
        super();
    }

    public TestCacheRegionFactory(final CacheKeysFactory cacheKeysFactory) {
        super(cacheKeysFactory);
    }

    public TestCacheRegionFactory(final HazelcastInstance instance) {
        super(instance);
    }

    @Override
    protected void prepareForUse(final SessionFactoryOptions settings, final Map configValues) {
        super.prepareForUse(settings, configValues);
        cleanupService = new CleanupService("TestCacheRegionFactory", CLEANUP_PERIOD);
    }
}
