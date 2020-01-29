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

import com.hazelcast.client.test.TestHazelcastFactory;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.hibernate.entity.DummyEntity;
import com.hazelcast.hibernate.entity.DummyProperty;
import com.hazelcast.hibernate.instance.HazelcastMockInstanceLoader;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.stat.SecondLevelCacheStatistics;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

public abstract class HibernateStatisticsTestSupport extends HibernateTestSupport {

    private final TestHazelcastFactory factory  = new TestHazelcastFactory();

    protected SessionFactory sf;
    protected SessionFactory sf2;

    @Before
    public void postConstruct() {
        HazelcastMockInstanceLoader loader = new HazelcastMockInstanceLoader();
        loader.setInstanceFactory(factory);
        sf = createSessionFactory(getCacheProperties(),  loader);
        sf2 = createSessionFactory(getCacheProperties(), loader);
    }

    @After
    public void preDestroy() {
        if (sf != null) {
            sf.close();
            sf = null;
        }
        if (sf2 != null) {
            sf2.close();
            sf2 = null;
        }
        Hazelcast.shutdownAll();
        factory.shutdownAll();
    }

    protected abstract Properties getCacheProperties();

    protected void insertDummyEntities(int count) {
        insertDummyEntities(sf, count, 0);
    }

    protected void insertDummyEntities(int count, int childCount) {
        insertDummyEntities(sf, count, childCount);
    }

    protected void insertAnnotatedEntities(int count) {
        insertAnnotatedEntities(sf, count);
    }

    protected List<DummyEntity> executeQuery() {
        Session session = sf.openSession();
        try {
            Query query = session.createQuery("from " + DummyEntity.class.getName());
            query.setCacheable(true);
            return query.list();
        } finally {
            session.close();
        }
    }

    protected ArrayList<DummyEntity> getDummyEntities(long untilId) {
        return getDummyEntities(sf, untilId);
    }

    protected Set<DummyProperty> getPropertiesOfEntity(long entityId) {
        Session session = sf.openSession();
        DummyEntity entity = session.get(DummyEntity.class, entityId);
        if (entity != null) {
            return entity.getProperties();
        } else {
            return null;
        }
    }

    protected void updateDummyEntityName(long id, String newName) {
        updateDummyEntityName(sf, id, newName);
    }

    protected void deleteDummyEntity(long id) throws Exception {
        deleteDummyEntity(sf, id);
    }

    protected void executeUpdateQuery(String queryString) throws RuntimeException {
        executeUpdateQuery(sf, queryString);
    }

    @Test
    public void testUpdateQueryCausesInvalidationOfEntireRegion() {
        insertDummyEntities(10);

        executeUpdateQuery(sf, "UPDATE DummyEntity set name = 'manually-updated' where id=2");

        sf.getStatistics().clear();

        getDummyEntities(sf, 10);

        SecondLevelCacheStatistics dummyEntityCacheStats = sf.getStatistics().getSecondLevelCacheStatistics(CACHE_ENTITY);
        assertEquals(10, dummyEntityCacheStats.getMissCount());
        assertEquals(0, dummyEntityCacheStats.getHitCount());
    }

    @Test
    public void testUpdateQueryCausesInvalidationOfEntireCollectionRegion() {
        insertDummyEntities(1, 10);

        //properties reference in DummyEntity is evicted because of custom SQL query on Collection region
        executeUpdateQuery(sf, "update DummyProperty ent set ent.key='manually-updated'");
        sf.getStatistics().clear();

        //property reference missed in cache.
        getPropertiesOfEntity(0);

        SecondLevelCacheStatistics dummyPropertyCacheStats = sf.getStatistics().getSecondLevelCacheStatistics(CACHE_ENTITY + ".properties");
        assertEquals(0, dummyPropertyCacheStats.getHitCount());
        assertEquals(1, dummyPropertyCacheStats.getMissCount());
    }
}
