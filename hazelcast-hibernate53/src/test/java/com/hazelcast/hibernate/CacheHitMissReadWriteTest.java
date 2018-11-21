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
import com.hazelcast.test.annotation.ParallelTest;
import com.hazelcast.test.annotation.QuickTest;
import org.hibernate.cache.spi.ExtendedStatisticsSupport;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cache.spi.support.DomainDataRegionTemplate;
import org.hibernate.cfg.Environment;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.stat.CacheRegionStatistics;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * Read and write access (strict) cache concurrency strategy of Hibernate.
 * Data may be added, removed and mutated.
 * Strict means data integrity is preserved strictly (by locks)
 * Write through cache
 */
@RunWith(HazelcastSerialClassRunner.class)
@Category({QuickTest.class, ParallelTest.class})
public class CacheHitMissReadWriteTest
        extends HibernateStatisticsTestSupport {

    protected String getCacheStrategy() {
        return AccessType.READ_WRITE.getExternalName();
    }

    @Override
    protected Properties getCacheProperties() {
        Properties props = new Properties();
        props.setProperty(Environment.CACHE_REGION_FACTORY, HazelcastCacheRegionFactory.class.getName());
        return props;
    }

    @Test
    public void testGetUpdateRemoveGet()
            throws Exception {
        insertDummyEntities(10, 4);
        //all 10 entities and 40 properties are cached
        CacheRegionStatistics dummyEntityCacheStats = sf.getStatistics().getDomainDataRegionStatistics(CACHE_ENTITY);
        CacheRegionStatistics dummyPropertyCacheStats = sf.getStatistics().getDomainDataRegionStatistics(CACHE_PROPERTY);

        sf.getCache().evictEntityData();
        sf.getCache().evictCollectionData();

        //miss 10 entities
        getDummyEntities(sf, 10);

        //hit 1 entity and 4 properties
        updateDummyEntityName(sf, 2, "updated");

        //hit 1 entity, hit 4 properties
        getPropertiesOfEntity(sf, 2);
        //hit 1 entity and 4 properties
        deleteDummyEntity(sf, 1);

        assertEquals(12, dummyPropertyCacheStats.getHitCount());
        assertEquals(0, dummyPropertyCacheStats.getMissCount());
        assertEquals(3, dummyEntityCacheStats.getHitCount());
        assertEquals(10, dummyEntityCacheStats.getMissCount());

    }

    @Test
    public void testUpdateShouldInvalidateEntryInCache() {
        insertDummyEntities(10, 4);
        //all 10 entities and 40 properties are cached
        DomainDataRegionTemplate regionTemplate = (DomainDataRegionTemplate) (((SessionFactoryImpl) sf).getCache()).getRegion(CACHE_ENTITY);
        ExtendedStatisticsSupport stats = ((HazelcastStorageAccessImpl) regionTemplate.getCacheStorageAccess()).getDelegate();

        sf.getCache().evictEntityData();
        sf.getCache().evictCollectionData();

        //miss 10 entities, 10 entities are cached
        getDummyEntities(sf, 10);

        //updates cache entity
        updateDummyEntityName(sf, 2, "updated");

        assertEquals(10, stats.getElementCountInMemory());
    }
}
