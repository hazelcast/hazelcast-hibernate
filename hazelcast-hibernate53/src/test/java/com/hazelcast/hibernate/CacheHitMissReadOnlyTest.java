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

import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.ParallelJVMTest;
import com.hazelcast.test.annotation.QuickTest;
import org.hibernate.cache.cfg.spi.EntityDataCachingConfig;
import org.hibernate.cache.spi.DomainDataRegion;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cache.spi.support.EntityReadOnlyAccess;
import org.hibernate.cfg.Environment;
import org.hibernate.stat.CacheRegionStatistics;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Read-only access cache concurrency strategy of Hibernate.
 * Data may be added and removed, but not mutated.
 */
@RunWith(HazelcastSerialClassRunner.class)
@Category({QuickTest.class, ParallelJVMTest.class})
public class CacheHitMissReadOnlyTest extends HibernateStatisticsTestSupport {

    @Override
    protected AccessType getCacheStrategy() {
        return AccessType.READ_ONLY;
    }

    @Override
    protected Properties getCacheProperties() {
        Properties props = new Properties();
        props.setProperty(Environment.CACHE_REGION_FACTORY, HazelcastCacheRegionFactory.class.getName());
        return props;
    }

    @Test
    public void testGetUpdateRemoveGet() throws Exception {
        insertDummyEntities(10, 4);
        //all 10 entities and 40 properties are cached
        CacheRegionStatistics dummyEntityCacheStats = sf.getStatistics().getDomainDataRegionStatistics(CACHE_ENTITY);
        CacheRegionStatistics dummyPropertyCacheStats = sf.getStatistics().getDomainDataRegionStatistics(CACHE_PROPERTY);

        sf.getCache().evictEntityData();
        sf.getCache().evictCollectionData();
        //miss 10 entities
        getDummyEntities(10);
        //hit 1 entity and 4 properties
        deleteDummyEntity(1);

        assertEquals(4, dummyPropertyCacheStats.getHitCount());
        assertEquals(0, dummyPropertyCacheStats.getMissCount());
        assertEquals(1, dummyEntityCacheStats.getHitCount());
        assertEquals(10, dummyEntityCacheStats.getMissCount());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testReadOnlyUpdate() {
        insertDummyEntities(1, 0);
        updateDummyEntityName(0, "updated");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAfterUpdateShouldThrowOnReadOnly() {
        DomainDataRegion hzRegion = mock(DomainDataRegion.class);
        EntityDataCachingConfig config = mock(EntityDataCachingConfig.class);
        EntityReadOnlyAccess readOnlyAccess = new EntityReadOnlyAccess(hzRegion, null, null, config);
        readOnlyAccess.afterUpdate(null, null, null, null, null, null);
    }
}
