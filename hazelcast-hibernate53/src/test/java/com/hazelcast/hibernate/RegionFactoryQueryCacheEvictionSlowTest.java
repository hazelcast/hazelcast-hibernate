/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.hibernate;

import com.hazelcast.config.MapConfig;
import com.hazelcast.hibernate.local.TestLocalCacheRegionFactory;
import com.hazelcast.hibernate.region.TestCacheRegionFactory;
import com.hazelcast.test.HazelcastSerialParametersRunnerFactory;
import com.hazelcast.test.annotation.SlowTest;
import org.hibernate.cache.spi.support.QueryResultsRegionTemplate;
import org.hibernate.cfg.Environment;
import org.hibernate.internal.SessionFactoryImpl;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;

import static com.hazelcast.hibernate.local.TestLocalCacheRegionFactory.CLEANUP_PERIOD;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(HazelcastSerialParametersRunnerFactory.class)
@Category(SlowTest.class)
public class RegionFactoryQueryCacheEvictionSlowTest extends HibernateSlowTestSupport {

    @Parameterized.Parameter
    public String configFile;

    @Parameterized.Parameter(1)
    public String regionFactory;

    @Parameterized.Parameters(name = "Executing: {0}, {1}")
    public static Collection<Object[]> parameters() {
        return Arrays.asList(
                /*
                 * This test is configured such that 60 entities should be evicted from the cache:
                 *   - number of entities (100) > cache max size (50).
                 * It is run for both types of cache region factory.
                 */
                new Object[]{"hazelcast-custom.xml", TestCacheRegionFactory.class.getName()},
                new Object[]{"hazelcast-custom.xml", TestLocalCacheRegionFactory.class.getName()},

                /*
                 * This test is configured such that 0 entities should be evicted from the cache:
                 *   - number of entities (100) < cache max size (150)
                 *   - ttl (30) > cleanup period (6)
                 * It is run for both types of cache region factory.
                 */
                new Object[]{"hazelcast-custom-region-factory-slow-test.xml", TestCacheRegionFactory.class.getName()},
                new Object[]{"hazelcast-custom-region-factory-slow-test.xml", TestLocalCacheRegionFactory.class.getName()}
        );
    }

    protected Properties getCacheProperties() {
        Properties props = new Properties();

        // Use a specific hazelcast xml config file for these tests
        props.setProperty(CacheEnvironment.CONFIG_FILE_PATH_LEGACY, configFile);

        // The test regionFactory uses a CleanupService with a shorter delay
        props.setProperty(Environment.CACHE_REGION_FACTORY, regionFactory);

        return props;
    }

    @Test
    public void testQueryCacheCleanup() {
        MapConfig mapConfig = getHazelcastInstance(sf).getConfig().getMapConfig("default-query-results-region");
        final float baseEvictionRate = 0.2f;
        final int numberOfEntities = 100;
        final int defaultCleanupPeriod = (int) CLEANUP_PERIOD;
        final int maxSize = mapConfig.getMaxSizeConfig().getSize();
        final int evictedItemCount = Math.max(0, numberOfEntities - maxSize + (int) (maxSize * baseEvictionRate));
        insertDummyEntities(numberOfEntities);
        for (int i = 0; i < numberOfEntities; i++) {
            executeQuery(sf, i);
        }

        QueryResultsRegionTemplate regionTemplate = (QueryResultsRegionTemplate) (((SessionFactoryImpl) sf).getCache()).getDefaultQueryResultsCache().getRegion();
        RegionCache cache = ((HazelcastStorageAccessImpl) regionTemplate.getStorageAccess()).getDelegate();
        assertEquals(numberOfEntities, cache.getElementCountInMemory());
        sleep(defaultCleanupPeriod + 1);

        assertEquals("Number of evictions", evictedItemCount, numberOfEntities - cache.getElementCountInMemory());
    }
}
