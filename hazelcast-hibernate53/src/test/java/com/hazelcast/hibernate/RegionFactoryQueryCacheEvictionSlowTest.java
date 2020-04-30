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

import static org.awaitility.Awaitility.await;

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
          new Object[]{"hazelcast-custom.xml", HazelcastCacheRegionFactory.class.getName()},
          new Object[]{"hazelcast-custom.xml", HazelcastLocalCacheRegionFactory.class.getName()}
          );
    }

    protected Properties getCacheProperties() {
        Properties props = new Properties();

        // Use a specific hazelcast xml config file for these tests
        props.setProperty(CacheEnvironment.CONFIG_FILE_PATH_LEGACY, configFile);
        props.setProperty(CacheEnvironment.CLEANUP_DELAY, "4");
        props.setProperty(Environment.CACHE_REGION_FACTORY, regionFactory);

        return props;
    }

    @Test
    public void testQueryCacheCleanup() {
        MapConfig mapConfig = getHazelcastInstance(sf).getConfig().getMapConfig("default-query-results-region");
        final float baseEvictionRate = 0.2f;
        final int numberOfEntities = 100;
        final int maxSize = mapConfig.getEvictionConfig().getSize();
        final int evictedItemCount = Math.max(0, numberOfEntities - maxSize + (int) (maxSize * baseEvictionRate));
        insertDummyEntities(numberOfEntities);
        for (int i = 0; i < numberOfEntities; i++) {
            executeQuery(sf, i);
        }

        QueryResultsRegionTemplate regionTemplate = (QueryResultsRegionTemplate) (((SessionFactoryImpl) sf).getCache())
          .getDefaultQueryResultsCache().getRegion();
        RegionCache cache = ((HazelcastStorageAccessImpl) regionTemplate.getStorageAccess()).getDelegate();

        await().atMost(5, TimeUnit.SECONDS)
          .until(() -> numberOfEntities == cache.getElementCountInMemory());

        await()
          .atMost(4 + 2, TimeUnit.SECONDS)
          .until(() -> (numberOfEntities - cache.getElementCountInMemory()) == evictedItemCount);
    }
}
