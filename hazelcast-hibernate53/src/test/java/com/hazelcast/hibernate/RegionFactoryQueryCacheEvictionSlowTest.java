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
import java.util.concurrent.TimeUnit;

import static com.hazelcast.hibernate.local.TestLocalCacheRegionFactory.CLEANUP_PERIOD;
import static org.awaitility.Awaitility.await;
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
        final int numberOfEntities = 100;
        final int maxSize = mapConfig.getEvictionConfig().getSize();

        insertDummyEntities(numberOfEntities);
        for (int i = 0; i < numberOfEntities; i++) {
            executeQuery(sf, i);
        }

        QueryResultsRegionTemplate regionTemplate = (QueryResultsRegionTemplate) (((SessionFactoryImpl) sf).getCache()).getDefaultQueryResultsCache().getRegion();
        RegionCache cache = ((HazelcastStorageAccessImpl) regionTemplate.getStorageAccess()).getDelegate();

        await().atMost(5, TimeUnit.SECONDS)
          .until(() -> numberOfEntities == cache.getElementCountInMemory());

        await()
          .atMost((int) (CLEANUP_PERIOD + 1), TimeUnit.SECONDS)
          .until(() -> (cache.getElementCountInMemory()) < maxSize);
    }
}
