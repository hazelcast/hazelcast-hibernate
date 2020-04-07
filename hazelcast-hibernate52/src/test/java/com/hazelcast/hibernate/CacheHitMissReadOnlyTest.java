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

import com.hazelcast.hibernate.access.ReadOnlyAccessDelegate;
import com.hazelcast.hibernate.region.HazelcastRegion;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.ParallelJVMTest;
import com.hazelcast.test.annotation.QuickTest;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cfg.Environment;
import org.hibernate.stat.SecondLevelCacheStatistics;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        SecondLevelCacheStatistics dummyEntityCacheStats = sf.getStatistics().getSecondLevelCacheStatistics(CACHE_ENTITY);
        SecondLevelCacheStatistics dummyPropertyCacheStats = sf.getStatistics().getSecondLevelCacheStatistics(CACHE_PROPERTY);

        sf.getCache().evictEntityRegions();
        sf.getCache().evictCollectionRegions();
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
    public void testUpdateQueryCausesInvalidationOfEntireRegion() {
        insertDummyEntities(10);
        executeUpdateQuery("UPDATE DummyEntity set name = 'manually-updated' where id=2");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testReadOnlyUpdate() {
        insertDummyEntities(1, 0);
        updateDummyEntityName(0, "updated");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAfterUpdateShouldThrowOnReadOnly() {
        HazelcastRegion hzRegion = mock(HazelcastRegion.class);
        when(hzRegion.getCache()).thenReturn(null);
        when(hzRegion.getLogger()).thenReturn(null);
        ReadOnlyAccessDelegate readOnlyAccessDelegate = new ReadOnlyAccessDelegate(hzRegion, null);
        readOnlyAccessDelegate.afterUpdate(null, null, null, null, null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testUpdateQueryCausesInvalidationOfEntireCollectionRegion() {
        insertDummyEntities(1, 10);

        //attempt to evict properties reference in DummyEntity because of custom SQL query on Collection region
        executeUpdateQuery("update DummyProperty ent set ent.key='manually-updated'");
    }
}
