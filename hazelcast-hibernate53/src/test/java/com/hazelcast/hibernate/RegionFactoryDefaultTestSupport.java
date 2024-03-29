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

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.hibernate.entity.DummyEntity;
import com.hazelcast.hibernate.entity.DummyProperty;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.Version;
import org.hibernate.cfg.Environment;
import org.hibernate.query.Query;
import org.hibernate.stat.Statistics;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public abstract class RegionFactoryDefaultTestSupport extends HibernateStatisticsTestSupport {

    protected abstract void clearTimestampsCache();

    protected Properties getCacheProperties() {
        Properties props = new Properties();
        props.setProperty(Environment.CACHE_REGION_FACTORY, HazelcastCacheRegionFactory.class.getName());
        return props;
    }

    @Test
    public void testEntity() {
        final HazelcastInstance hz = HazelcastAccessor.getHazelcastInstance(sf);
        assertNotNull(hz);
        final int count = 100;
        final int childCount = 3;
        insertDummyEntities(count, childCount);
        List<DummyEntity> list = new ArrayList<DummyEntity>(count);
        Session session = sf.openSession();
        try {
            for (int i = 0; i < count; i++) {
                DummyEntity e = session.get(DummyEntity.class, (long) i);
                session.evict(e);
                list.add(e);
            }
        } finally {
            session.close();
        }
        session = sf.openSession();
        Transaction tx = session.beginTransaction();
        try {
            for (DummyEntity dummy : list) {
                dummy.setDate(new Date());
                session.update(dummy);
            }
            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            e.printStackTrace();
        } finally {
            session.close();
        }

        Statistics stats = sf.getStatistics();
        Map<?, ?> cache = hz.getMap(DummyEntity.class.getName());
        Map<?, ?> propCache = hz.getMap(DummyProperty.class.getName());
        Map<?, ?> propCollCache = hz.getMap(DummyEntity.class.getName() + ".properties");
        assertEquals((childCount + 1) * count, stats.getEntityInsertCount());
        // twice put of entity and properties (on load and update) and once put of collection
        // TODO: fix next assertion ->
        //        assertEquals((childCount + 1) * count * 2, stats.getSecondLevelCachePutCount());
        assertEquals(childCount * count, stats.getEntityLoadCount());
        assertEquals(count, stats.getSecondLevelCacheHitCount());
        // collection cache miss
        assertEquals(count, stats.getSecondLevelCacheMissCount());
        assertEquals(count, cache.size());
        assertEquals(count * childCount, propCache.size());
        assertEquals(count, propCollCache.size());
        sf.getCache().evictEntityData(DummyEntity.class);
        sf.getCache().evictEntityData(DummyProperty.class);
        assertEquals(0, cache.size());
        assertEquals(0, propCache.size());
        stats.logSummary();
    }

    @Test
    public void testQuery() {
        final int entityCount = 10;
        final int queryCount = 3;
        insertDummyEntities(entityCount);

        // Clear the caches after inserting the entities (to avoid scenarios where the timestamps cache may or may not
        // be out of date, leading to a different number of cache hits and misses).
        sf.getCache().evictAllRegions();
        clearTimestampsCache();

        List<DummyEntity> list = null;
        for (int i = 0; i < queryCount; i++) {
            list = executeQuery();
            assertEquals(entityCount, list.size());
        }

        Session session = sf.openSession();
        Transaction tx = session.beginTransaction();
        try {
            for (DummyEntity dummy : list) {
                session.delete(dummy);
            }
            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            e.printStackTrace();
        } finally {
            session.close();
        }

        Statistics stats = sf.getStatistics();
        assertEquals(1, stats.getQueryCachePutCount());
        assertEquals(1, stats.getQueryCacheMissCount());
        assertEquals(queryCount - 1, stats.getQueryCacheHitCount());
        assertEquals(1, stats.getQueryExecutionCount());
        assertEquals(entityCount, stats.getEntityInsertCount());
        //      FIXME
        //      HazelcastRegionFactory puts into L2 cache 2 times; 1 on insert, 1 on query execution
        //      assertEquals(entityCount, stats.getSecondLevelCachePutCount());
        assertEquals(entityCount, stats.getEntityLoadCount());

        // see https://github.com/hibernate/hibernate-orm/blob/6.0/migration-guide.adoc#query-result-cache
        boolean entitiesInQueryCache = hibernateMajorVersion() >= 6;
        assertEquals(entityCount * (queryCount - 1) * (entitiesInQueryCache ? 1 : 2), stats.getSecondLevelCacheHitCount());
        // collection cache miss
        assertEquals(entityCount, stats.getSecondLevelCacheMissCount());
        assertEquals(entityCount, stats.getEntityDeleteCount());
        stats.logSummary();
    }

    private int hibernateMajorVersion() {
        String hibernateVersion = Version.getVersionString();
        return Integer.parseInt(hibernateVersion.split("\\.")[0]);
    }

    @Test
    public void testSpecificQueryRegionEviction() {
        int entityCount = 10;
        insertDummyEntities(entityCount, 0);

        //miss 1 query list entities
        Session session = sf.openSession();
        Transaction txn = session.beginTransaction();
        Query query = session.createQuery("from " + DummyEntity.class.getName());
        query.setCacheable(true).setCacheRegion("newregionname");
        query.list();
        txn.commit();
        session.close();
        //query is cached

        //query is invalidated
        sf.getCache().evictQueryRegion("newregionname");

        //miss 1 query
        session = sf.openSession();
        txn = session.beginTransaction();
        query = session.createQuery("from " + DummyEntity.class.getName());
        query.setCacheable(true);
        query.list();
        txn.commit();
        session.close();

        assertEquals(0, sf.getStatistics().getQueryCacheHitCount());
        assertEquals(2, sf.getStatistics().getQueryCacheMissCount());
    }

    @Test
    public void testInsertGetUpdateGet() {
        Session session = sf.openSession();
        DummyEntity e = new DummyEntity(1L, "test", 0d, null);
        Transaction tx = session.beginTransaction();
        try {
            session.save(e);
            tx.commit();
        } catch (Exception ex) {
            ex.printStackTrace();
            tx.rollback();
            Assert.fail(ex.getMessage());
        } finally {
            session.close();
        }

        session = sf.openSession();
        try {
            e = session.get(DummyEntity.class, 1L);
            assertEquals("test", e.getName());
            assertNull(e.getDate());
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail(ex.getMessage());
        } finally {
            session.close();
        }

        session = sf.openSession();
        tx = session.beginTransaction();
        try {
            e = session.get(DummyEntity.class, 1L);
            assertEquals("test", e.getName());
            assertNull(e.getDate());
            e.setName("dummy");
            e.setDate(new Date());
            session.update(e);
            tx.commit();
        } catch (Exception ex) {
            ex.printStackTrace();
            tx.rollback();
            Assert.fail(ex.getMessage());
        } finally {
            session.close();
        }

        session = sf.openSession();
        try {
            e = session.get(DummyEntity.class, 1L);
            assertEquals("dummy", e.getName());
            Assert.assertNotNull(e.getDate());
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail(ex.getMessage());
        } finally {
            session.close();
        }
    }

    @Test
    public void testEvictionEntity() {
        insertDummyEntities(1, 1);
        sf.getCache().evictEntityData("com.hazelcast.hibernate.entity.DummyEntity", 0L);
        assertFalse(sf.getCache().containsEntity("com.hazelcast.hibernate.entity.DummyEntity", 0L));
    }

    @Test
    public void testEvictionCollection() {
        insertDummyEntities(1, 1);
        sf.getCache().evictCollectionData("com.hazelcast.hibernate.entity.DummyEntity.properties", 0L);
        assertFalse(sf.getCache().containsCollection("com.hazelcast.hibernate.entity.DummyEntity.properties", 0L));
    }

    @Test
    public void testInsertEvictUpdate() {
        insertDummyEntities(1);
        Session session = sf.openSession();
        Transaction tx = session.beginTransaction();
        DummyEntity ent = session.get(DummyEntity.class, 0L);
        sf.getCache().evictEntityData("com.hazelcast.hibernate.entity.DummyEntity", 0L);
        ent.setName("updatedName");
        session.update(ent);
        tx.commit();
        session.close();
        ArrayList<DummyEntity> list = getDummyEntities(1);
        assertEquals("updatedName", list.get(0).getName());
    }
}
