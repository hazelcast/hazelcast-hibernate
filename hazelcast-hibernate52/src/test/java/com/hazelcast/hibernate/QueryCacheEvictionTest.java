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
import org.hibernate.cache.spi.QueryResultsRegion;
import org.hibernate.cfg.Environment;
import org.hibernate.internal.CacheImpl;
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
public class QueryCacheEvictionTest extends HibernateSlowTestSupport {

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
          new Object[]{"hazelcast-custom.xml", HazelcastCacheRegionFactory.class.getName()},
          new Object[]{"hazelcast-custom.xml", HazelcastLocalCacheRegionFactory.class.getName()},

          /*
           * This test is configured such that 0 entities should be evicted from the cache:
           *   - number of entities (100) < cache max size (150)
           *   - ttl (30) > cleanup period (6)
           * It is run for both types of cache region factory.
           */
          new Object[]{"hazelcast-custom-region-factory-slow-test.xml", HazelcastCacheRegionFactory.class.getName()},
          new Object[]{"hazelcast-custom-region-factory-slow-test.xml", HazelcastLocalCacheRegionFactory.class.getName()}
        );
    }

    protected Properties getCacheProperties() {
        Properties props = new Properties();
        props.setProperty(CacheEnvironment.CONFIG_FILE_PATH, configFile);
        props.setProperty(Environment.CACHE_REGION_FACTORY, regionFactory);
        return props;
    }

    @Test
    public void testQueryCacheCleanup() {
        MapConfig mapConfig = getHazelcastInstance(sf).getConfig().getMapConfig("org.hibernate.cache.*");
        final int numberOfEntities = 100;
        final int maxSize = mapConfig.getEvictionConfig().getSize();
        int initialEntries = Math.min(maxSize, numberOfEntities);

        insertDummyEntities(numberOfEntities);
        for (int i = 0; i < numberOfEntities; i++) {
            executeQuery(sf, i);
        }

        QueryResultsRegion cache = ((CacheImpl) sf.getCache()).getQueryCache().getRegion();

        await().atMost(1, TimeUnit.SECONDS)
          .until(() -> initialEntries == cache.getElementCountInMemory());

        await()
          .atMost(2, TimeUnit.SECONDS)
          .until(() -> (numberOfEntities - cache.getElementCountInMemory()) == Math.max(0, numberOfEntities - maxSize));
    }
}
