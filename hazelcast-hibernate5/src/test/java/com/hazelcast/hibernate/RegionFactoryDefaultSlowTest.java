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
import com.hazelcast.hibernate.entity.DummyEntity;
import com.hazelcast.hibernate.region.HazelcastQueryResultsRegion;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.SlowTest;
import org.awaitility.Awaitility;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.cfg.Environment;
import org.hibernate.internal.SessionFactoryImpl;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;

@RunWith(HazelcastSerialClassRunner.class)
@Category(SlowTest.class)
public class RegionFactoryDefaultSlowTest
        extends HibernateSlowTestSupport {

    protected Properties getCacheProperties() {
        Properties props = new Properties();
        props.setProperty(Environment.CACHE_REGION_FACTORY, HazelcastCacheRegionFactory.class.getName());
        return props;
    }

    @Test
    public void testQueryCacheCleanup() {
        MapConfig mapConfig = getHazelcastInstance(sf).getConfig().getMapConfig("org.hibernate.cache.*");
        final int numberOfEntities = 100;
        final int maxSize = Math.min(mapConfig.getEvictionConfig().getSize(), numberOfEntities);

        insertDummyEntities(numberOfEntities);
        for (int i = 0; i < numberOfEntities; i++) {
            executeQuery(sf, i);
        }

        HazelcastQueryResultsRegion queryRegion = ((HazelcastQueryResultsRegion) (((SessionFactoryImpl) sf).getQueryCache()).getRegion());
        assertEquals(numberOfEntities, queryRegion.getCache().size());

        await()
          .atMost(61, TimeUnit.SECONDS)
          .until(() -> (queryRegion.getCache().size() < maxSize));
    }

    @Test
    public void testUpdateEntity() {
        final long dummyId = 0;
        Session session = sf.openSession();
        Transaction tx = session.beginTransaction();
        session.save(new DummyEntity(dummyId, null, 0, null));
        tx.commit();

        tx = session.beginTransaction();
        DummyEntity ent = session.get(DummyEntity.class, dummyId);
        ent.setName("updatedName");
        session.update(ent);
        tx.commit();
        session.close();

        session = sf2.openSession();
        DummyEntity entity = session.get(DummyEntity.class, dummyId);
        assertEquals("updatedName", entity.getName());
    }
}
