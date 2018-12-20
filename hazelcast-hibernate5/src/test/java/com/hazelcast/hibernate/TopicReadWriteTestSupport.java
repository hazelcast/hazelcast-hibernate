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

import com.hazelcast.hibernate.entity.AnnotatedEntity;
import com.hazelcast.hibernate.entity.DummyEntity;
import com.hazelcast.hibernate.entity.DummyProperty;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.cache.spi.access.AccessType;

import org.junit.Test;

import java.util.*;

public abstract class TopicReadWriteTestSupport extends HibernateTopicTestSupport {

    @Test
    public void testReplaceCollection() {
        insertDummyEntities(sf, 2, 4);

        Session session = sf.openSession();
        Transaction transaction = session.beginTransaction();

        DummyProperty property = new DummyProperty("somekey");
        session.save(property);

        DummyEntity entity = session.get(DummyEntity.class, 1L);
        HashSet<DummyProperty> updatedProperties = new HashSet<DummyProperty>();
        updatedProperties.add(property);
        entity.setProperties(updatedProperties);

        session.update(entity);

        transaction.commit();
        session.close();

        assertTopicNotifications(1, CACHE_ENTITY);
        assertTopicNotifications(2, CACHE_ENTITY_PROPERTIES);
        assertTopicNotifications(18, CACHE_TIMESTAMPS_REGION);
    }

    @Test
    public void testUpdateOneEntityByNaturalId() {
        insertAnnotatedEntities(sf, 2);

        Session session = sf.openSession();
        Transaction tx = session.beginTransaction();

        AnnotatedEntity toBeUpdated = session.byNaturalId(AnnotatedEntity.class).using("title", "dummy:1").getReference();
        toBeUpdated.setTitle("dummy101");
        tx.commit();
        session.close();

        assertTopicNotifications(2, CACHE_ANNOTATED_ENTITY + "##NaturalId");
        assertTopicNotifications(4, CACHE_TIMESTAMPS_REGION);
    }

    @Test
    public void testUpdateEntitiesByNaturalId() {
        insertAnnotatedEntities(sf, 2);

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
        assertTopicNotifications(4, CACHE_TIMESTAMPS_REGION);
    }

    @Test
    public void testDeleteOneEntityByNaturalId() {
        insertAnnotatedEntities(sf, 2);

        Session session = sf.openSession();
        Transaction tx = session.beginTransaction();

        AnnotatedEntity toBeDeleted = session.byNaturalId(AnnotatedEntity.class).using("title", "dummy:1").getReference();
        session.delete(toBeDeleted);
        tx.commit();
        session.close();

        assertTopicNotifications(2, CACHE_ANNOTATED_ENTITY + "##NaturalId");
        assertTopicNotifications(4, CACHE_TIMESTAMPS_REGION);
    }

    @Test
    public void testDeleteEntitiesByNaturalId() {
        insertAnnotatedEntities(sf, 2);

        Session session = sf.openSession();
        Transaction tx = session.beginTransaction();

        AnnotatedEntity toBeDeleted = session.byNaturalId(AnnotatedEntity.class).using("title", "dummy:1").getReference();
        session.delete(toBeDeleted);

        AnnotatedEntity toBeDeleted2 = session.byNaturalId(AnnotatedEntity.class).using("title", "dummy:0").getReference();
        session.delete(toBeDeleted2);
        tx.commit();
        session.close();

        assertTopicNotifications(4, CACHE_ANNOTATED_ENTITY + "##NaturalId");
        assertTopicNotifications(4, CACHE_TIMESTAMPS_REGION);
    }

    @Test
    public void testDeleteOneEntity() throws Exception {
        insertDummyEntities(sf, 1, 1);

        deleteDummyEntity(sf, 0);

        assertTopicNotifications(1, CACHE_ENTITY);
        assertTopicNotifications(1, CACHE_ENTITY_PROPERTIES);
        assertTopicNotifications(1, CACHE_PROPERTY);
        assertTopicNotifications(9, CACHE_TIMESTAMPS_REGION);
    }

    @Test
    public void testDeleteEntities() throws Exception {
        insertDummyEntities(sf, 10, 4);

        for (int i = 0; i < 3; i++) {
            deleteDummyEntity(sf, i);
        }

        assertTopicNotifications(3, CACHE_ENTITY);
        assertTopicNotifications(3, CACHE_ENTITY_PROPERTIES);
        assertTopicNotifications(12, CACHE_PROPERTY);
        assertTopicNotifications(67, CACHE_TIMESTAMPS_REGION);
    }

    @Test
    public void testUpdateOneEntity() {
        insertDummyEntities(sf, 10, 4);

        getDummyEntities(sf, 10);

        updateDummyEntityName(sf, 2, "updated");

        assertTopicNotifications(1, CACHE_ENTITY);
        assertTopicNotifications(0, CACHE_ENTITY_PROPERTIES);
        assertTopicNotifications(0, CACHE_PROPERTY);
        assertTopicNotifications(54, CACHE_TIMESTAMPS_REGION);
    }

    @Test
    public void testUpdateEntities() {
        insertDummyEntities(sf, 1, 10);

        executeUpdateQuery(sf, "update DummyEntity set name = 'updated-name' where id < 2");

        assertTopicNotifications(1, CACHE_ENTITY);
        assertTopicNotifications(0, CACHE_ENTITY_PROPERTIES);
        assertTopicNotifications(0, CACHE_PROPERTY);
        assertTopicNotifications(15, CACHE_TIMESTAMPS_REGION);
    }

    @Override
    protected AccessType getCacheStrategy() {
        return AccessType.READ_WRITE;
    }
}
