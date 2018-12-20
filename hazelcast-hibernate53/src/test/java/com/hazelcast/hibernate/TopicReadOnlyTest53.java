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
import com.hazelcast.test.annotation.SlowTest;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(HazelcastSerialClassRunner.class)
@Category(SlowTest.class)
public class TopicReadOnlyTest53 extends TopicReadOnlyTestSupport {

    @Test
    public void testUpdateQueryByNaturalId() {
        insertAnnotatedEntities(sf, 2);

        executeUpdateQuery(sf, "update AnnotatedEntity set title = 'updated-name' where title = 'dummy:1'");

        // There are *2* topic notifications (compared to *1* on previous Hibernate versions):
        // - removeAll is called after executing the update
        // - unlockRegion is called after the transaction completes
        assertTopicNotifications(2, CACHE_ANNOTATED_ENTITY + "##NaturalId");
        assertTopicNotifications(4, CACHE_TIMESTAMPS_REGION);
    }

    @Test
    public void testDeleteOneEntity() throws Exception {
        insertDummyEntities(sf, 1, 1);

        deleteDummyEntity(sf, 0);

        assertTopicNotifications(2, CACHE_ENTITY);
        // There is only *1* topic notification (compared to *2* - which are both an evict by the key - on previous
        // Hibernate versions)
        assertTopicNotifications(1, CACHE_ENTITY_PROPERTIES);
        assertTopicNotifications(2, CACHE_PROPERTY);
        assertTopicNotifications(9, CACHE_TIMESTAMPS_REGION);
    }

    @Test
    public void testDeleteEntities() throws Exception {
        insertDummyEntities(sf, 10, 4);

        for (int i = 0; i < 3; i++) {
            deleteDummyEntity(sf, i);
        }

        assertTopicNotifications(6, CACHE_ENTITY);
        assertTopicNotifications(3, CACHE_ENTITY_PROPERTIES);
        assertTopicNotifications(24, CACHE_PROPERTY);
        assertTopicNotifications(67, CACHE_TIMESTAMPS_REGION);
    }

    // This test should throw an UnsupportedOperationException, for attempting an update on a read-only cache,
    // but in Hibernate 5.3 the exception is not thrown.
    @Ignore
    @Test(expected = UnsupportedOperationException.class)
    public void testUpdateEntities() {
        insertDummyEntities(sf, 1, 10);

        executeUpdateQuery(sf, "update DummyEntity set name = 'updated-name' where id < 2");
    }

    // This test should throw an UnsupportedOperationException, for attempting an update on a read-only cache,
    // but in Hibernate 5.3 the exception is not thrown.
    @Ignore
    @Test(expected = UnsupportedOperationException.class)
    public void testUpdateEntitiesAndProperties() {
        insertDummyEntities(sf, 1, 10);

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
        insertDummyEntities(sf, 1, 10);

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
