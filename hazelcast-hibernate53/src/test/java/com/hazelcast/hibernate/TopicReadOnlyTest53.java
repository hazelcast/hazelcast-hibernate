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
import com.hazelcast.hibernate.local.LocalRegionCache;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.SlowTest;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.cache.spi.RegionFactory;
import org.hibernate.query.Query;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.mock;

@RunWith(HazelcastSerialClassRunner.class)
@Category(SlowTest.class)
public class TopicReadOnlyTest53 extends TopicReadOnlyTestSupport {

    @Override
    protected void configureTopic(HazelcastInstance instance) {
        // Construct a LocalRegionCache instance, which configures the topic
        new LocalRegionCache(mock(RegionFactory.class), "cache", instance, null, true);
    }

    @Override
    protected String getTimestampsRegionName() {
        return "default-update-timestamps-region";
    }

    @Test
    public void testUpdateQueryByNaturalId() {
        insertAnnotatedEntities(2);

        executeUpdateQuery("update AnnotatedEntity set title = 'updated-name' where title = 'dummy:1'");

        // There are *2* topic notifications (compared to *1* on previous Hibernate versions):
        // - removeAll is called after executing the update
        // - unlockRegion is called after the transaction completes
        assertTopicNotifications(2, CACHE_ANNOTATED_ENTITY + "##NaturalId");
        assertTopicNotifications(4, getTimestampsRegionName());
    }

    @Test
    public void testDeleteOneEntity() throws Exception {
        insertDummyEntities(1, 1);

        deleteDummyEntity(0);

        assertTopicNotifications(2, CACHE_ENTITY);
        // There is only *1* topic notification (compared to *2* - which are both an evict by the key - on previous
        // Hibernate versions)
        assertTopicNotifications(1, CACHE_ENTITY_PROPERTIES);
        assertTopicNotifications(2, CACHE_PROPERTY);
        assertTopicNotifications(9, getTimestampsRegionName());
    }

    @Test
    public void testDeleteEntities() throws Exception {
        insertDummyEntities(10, 4);

        for (int i = 0; i < 3; i++) {
            deleteDummyEntity(i);
        }

        assertTopicNotifications(6, CACHE_ENTITY);
        assertTopicNotifications(3, CACHE_ENTITY_PROPERTIES);
        assertTopicNotifications(24, CACHE_PROPERTY);
        assertTopicNotifications(67, getTimestampsRegionName());
    }

    // This test should throw an UnsupportedOperationException, for attempting an update on a read-only cache,
    // but in Hibernate 5.3 the exception is not thrown.
    @Ignore
    @Test(expected = UnsupportedOperationException.class)
    public void testUpdateEntities() {
        insertDummyEntities(1, 10);

        executeUpdateQuery("update DummyEntity set name = 'updated-name' where id < 2");
    }

    // This test should throw an UnsupportedOperationException, for attempting an update on a read-only cache,
    // but in Hibernate 5.3 the exception is not thrown.
    @Ignore
    @Test(expected = UnsupportedOperationException.class)
    public void testUpdateEntitiesAndProperties() {
        insertDummyEntities(1, 10);

        Session session = null;
        Transaction txn = null;
        try {
            session = sf.openSession();
            txn = session.beginTransaction();
            Query query = session.createQuery("update DummyEntity set name = 'updated-name' where id < 2");
            query.setCacheable(true);
            query.executeUpdate();

            Query query2 = session.createQuery("update DummyProperty set version = version + 1");
            query2.setCacheable(true);
            query2.executeUpdate();

            txn.commit();
        } catch (RuntimeException e) {
            txn.rollback();
            e.printStackTrace();
            throw e;
        } finally {
            session.close();
        }
    }

    // This test should throw an UnsupportedOperationException, for attempting an update on a read-only cache,
    // but in Hibernate 5.3 the exception is not thrown.
    @Ignore
    @Test(expected = UnsupportedOperationException.class)
    public void testUpdateOneEntityAndProperties() {
        insertDummyEntities(1, 10);

        Session session = null;
        Transaction txn = null;
        try {
            session = sf.openSession();
            txn = session.beginTransaction();
            Query query = session.createQuery("update DummyEntity set name = 'updated-name' where id = 0");
            query.setCacheable(true);
            query.executeUpdate();

            Query query2 = session.createQuery("update DummyProperty set version = version + 1");
            query2.setCacheable(true);
            query2.executeUpdate();

            txn.commit();
        } catch (RuntimeException e) {
            txn.rollback();
            e.printStackTrace();
            throw e;
        } finally {
            session.close();
        }
    }
}
