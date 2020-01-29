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
import com.hazelcast.hibernate.entity.AnnotatedEntity;
import com.hazelcast.hibernate.entity.DummyEntity;
import com.hazelcast.hibernate.entity.DummyProperty;
import com.hazelcast.hibernate.local.LocalRegionCache;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.SlowTest;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.cache.spi.RegionFactory;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.query.Query;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.HashSet;

import static org.mockito.Mockito.mock;

@RunWith(HazelcastSerialClassRunner.class)
@Category(SlowTest.class)
public class TopicTransactionalTest53 extends HibernateTopicTestSupport {

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
    public void testReplaceCollection() {
        insertDummyEntities(2, 4);

        Session session = sf.openSession();
        Transaction transaction = session.beginTransaction();

        DummyProperty property = new DummyProperty("somekey");
        session.save(property);

        DummyEntity entity = session.get(DummyEntity.class, 1L);
        HashSet<DummyProperty> updatedProperties = new HashSet<>();
        updatedProperties.add(property);
        entity.setProperties(updatedProperties);

        session.update(entity);

        transaction.commit();
        session.close();

        assertTopicNotifications(1, CACHE_ENTITY);
        assertTopicNotifications(4, CACHE_ENTITY_PROPERTIES);
        assertTopicNotifications(18, getTimestampsRegionName());
    }

    @Test
    public void testUpdateOneEntityByNaturalId() {
        insertAnnotatedEntities(2);

        Session session = sf.openSession();
        Transaction tx = session.beginTransaction();

        AnnotatedEntity toBeUpdated = session.byNaturalId(AnnotatedEntity.class).using("title", "dummy:1").getReference();
        toBeUpdated.setTitle("dummy101");
        tx.commit();
        session.close();

        assertTopicNotifications(2, CACHE_ANNOTATED_ENTITY + "##NaturalId");
        assertTopicNotifications(4, getTimestampsRegionName());
    }

    @Test
    public void testUpdateEntitiesByNaturalId() {
        insertAnnotatedEntities(2);

        Session session = sf.openSession();
        Transaction tx = session.beginTransaction();

        AnnotatedEntity toBeUpdated = session.byNaturalId(AnnotatedEntity.class).using("title", "dummy:1").getReference();
        toBeUpdated.setTitle("dummy101");

        AnnotatedEntity toBeUpdated2 = session.byNaturalId(AnnotatedEntity.class).using("title", "dummy:0").getReference();
        toBeUpdated2.setTitle("dummy100");
        tx.commit();
        session.close();

        // 5 notifications = 1 eviction, plus for each update: one unlockItem and one afterUpdate
        assertTopicNotifications(5, CACHE_ANNOTATED_ENTITY + "##NaturalId");
        assertTopicNotifications(4, getTimestampsRegionName());
    }

    @Test
    public void testUpdateQueryByNaturalId() {
        insertAnnotatedEntities(2);

        executeUpdateQuery("update AnnotatedEntity set title = 'updated-name' where title = 'dummy:1'");

        assertTopicNotifications(2, CACHE_ANNOTATED_ENTITY + "##NaturalId");
        assertTopicNotifications(4, getTimestampsRegionName());
    }

    @Test
    public void testDeleteOneEntityByNaturalId() {
        insertAnnotatedEntities(2);

        Session session = sf.openSession();
        Transaction tx = session.beginTransaction();

        AnnotatedEntity toBeDeleted = session.byNaturalId(AnnotatedEntity.class).using("title", "dummy:1").getReference();
        session.delete(toBeDeleted);
        tx.commit();
        session.close();

        assertTopicNotifications(2, CACHE_ANNOTATED_ENTITY + "##NaturalId");
        assertTopicNotifications(4, getTimestampsRegionName());
    }

    @Test
    public void testDeleteEntitiesByNaturalId() {
        insertAnnotatedEntities(2);

        Session session = sf.openSession();
        Transaction tx = session.beginTransaction();

        AnnotatedEntity toBeDeleted = session.byNaturalId(AnnotatedEntity.class).using("title", "dummy:1").getReference();
        session.delete(toBeDeleted);

        AnnotatedEntity toBeDeleted2 = session.byNaturalId(AnnotatedEntity.class).using("title", "dummy:0").getReference();
        session.delete(toBeDeleted2);
        tx.commit();
        session.close();

        assertTopicNotifications(4, CACHE_ANNOTATED_ENTITY + "##NaturalId");
        assertTopicNotifications(4, getTimestampsRegionName());
    }

    @Test
    public void testDeleteOneEntity() throws Exception {
        insertDummyEntities(1, 1);

        deleteDummyEntity(0);

        assertTopicNotifications(2, CACHE_ENTITY);
        assertTopicNotifications(2, CACHE_ENTITY_PROPERTIES);
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
        assertTopicNotifications(6, CACHE_ENTITY_PROPERTIES);
        assertTopicNotifications(24, CACHE_PROPERTY);
        assertTopicNotifications(67, getTimestampsRegionName());
    }

    @Test
    public void testUpdateOneEntity() {
        insertDummyEntities(10, 4);

        getDummyEntities(10);

        updateDummyEntityName(2, "updated");

        assertTopicNotifications(1, CACHE_ENTITY);
        assertTopicNotifications(0, CACHE_ENTITY_PROPERTIES);
        assertTopicNotifications(0, CACHE_PROPERTY);
        assertTopicNotifications(54, getTimestampsRegionName());
    }

    @Test
    public void testUpdateEntities() {
        insertDummyEntities(1, 10);

        executeUpdateQuery("update DummyEntity set name = 'updated-name' where id < 2");

        assertTopicNotifications(2, CACHE_ENTITY);
        assertTopicNotifications(0, CACHE_ENTITY_PROPERTIES);
        assertTopicNotifications(0, CACHE_PROPERTY);
        assertTopicNotifications(15, getTimestampsRegionName());
    }

    @Test
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

        assertTopicNotifications(2, CACHE_ENTITY);
        assertTopicNotifications(2, CACHE_ENTITY_PROPERTIES);
        assertTopicNotifications(2, CACHE_PROPERTY);
        assertTopicNotifications(17, getTimestampsRegionName());
    }

    @Test
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

        assertTopicNotifications(2, CACHE_ENTITY);
        assertTopicNotifications(2, CACHE_ENTITY_PROPERTIES);
        assertTopicNotifications(2, CACHE_PROPERTY);
        assertTopicNotifications(17, getTimestampsRegionName());
    }

    @Override
    protected AccessType getCacheStrategy() {
        return AccessType.TRANSACTIONAL;
    }
}
