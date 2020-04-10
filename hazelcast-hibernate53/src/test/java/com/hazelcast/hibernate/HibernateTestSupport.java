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

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.hibernate.entity.AnnotatedEntity;
import com.hazelcast.hibernate.entity.DummyEntity;
import com.hazelcast.hibernate.entity.DummyProperty;
import com.hazelcast.hibernate.instance.HazelcastAccessor;
import com.hazelcast.hibernate.instance.HazelcastMockInstanceFactory;
import com.hazelcast.hibernate.instance.IHazelcastInstanceLoader;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.internal.nio.IOUtil;
import com.hazelcast.test.HazelcastTestSupport;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cfg.Configuration;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;

public abstract class HibernateTestSupport extends HazelcastTestSupport {

    private final ILogger logger = Logger.getLogger(getClass());

    protected final String CACHE_ENTITY = DummyEntity.class.getName();
    protected final String CACHE_PROPERTY = DummyProperty.class.getName();

    @BeforeClass
    @AfterClass
    public static void tearUpAndDown() {
        Hazelcast.shutdownAll();
    }

    @After
    public final void cleanup() {
        Hazelcast.shutdownAll();
    }

    protected AccessType getCacheStrategy() {
        return AccessType.READ_WRITE;
    }

    protected void addHbmMappings(final Configuration conf) {
        conf.addFile(createHbmXml("DummyEntity"));
        conf.addFile(createHbmXml("DummyProperty"));
    }

    protected void addMappings(final Configuration conf) {
        conf.addAnnotatedClass(AnnotatedEntity.class);
        addHbmMappings(conf);
    }

    protected SessionFactory createSessionFactory(final Properties props,
                                                  final IHazelcastInstanceLoader customInstanceLoader) {
        props.put(CacheEnvironment.EXPLICIT_VERSION_CHECK, "true");
        if (customInstanceLoader != null) {
            HazelcastMockInstanceFactory.setThreadLocalLoader(customInstanceLoader);
            props.setProperty("hibernate.cache.hazelcast.factory", "com.hazelcast.hibernate.instance.HazelcastMockInstanceFactory");
            customInstanceLoader.configure(props);
        } else {
            props.remove("hibernate.cache.hazelcast.factory");
        }

        final Configuration conf = new Configuration();
        conf.configure(HibernateTestSupport.class.getClassLoader().getResource("test-hibernate.cfg.xml"));
        addMappings(conf);
        conf.addProperties(props);

        final SessionFactory sf = conf.buildSessionFactory();
        sf.getStatistics().setStatisticsEnabled(true);

        return sf;
    }

    protected HazelcastInstance getHazelcastInstance(final SessionFactory sf) {
        return HazelcastAccessor.getHazelcastInstance(sf);
    }

    protected void deleteDummyEntity(SessionFactory sf, long id) throws Exception {
        Session session = null;
        Transaction txn = null;
        try {
            session = sf.openSession();
            txn = session.beginTransaction();
            DummyEntity entityToDelete = session.get(DummyEntity.class, id);
            session.delete(entityToDelete);
            txn.commit();
        } catch (Exception e) {
            txn.rollback();
            e.printStackTrace();
            throw e;
        } finally {
            session.close();
        }
    }

    protected void executeUpdateQuery(SessionFactory sf, String queryString)
            throws RuntimeException {
        Session session = null;
        Transaction txn = null;
        try {
            session = sf.openSession();
            txn = session.beginTransaction();
            Query query = session.createQuery(queryString);
            query.setCacheable(true);
            query.executeUpdate();
            txn.commit();
        } catch (RuntimeException e) {
            txn.rollback();
            e.printStackTrace();
            throw e;
        } finally {
            session.close();
        }
    }

    protected ArrayList<DummyEntity> getDummyEntities(SessionFactory sf, long untilId) {
        Session session = sf.openSession();
        ArrayList<DummyEntity> entities = new ArrayList<DummyEntity>();
        for (long i=0; i<untilId; i++) {
            DummyEntity entity = session.get(DummyEntity.class, i);
            if (entity != null) {
                session.evict(entity);
                entities.add(entity);
            }
        }
        session.close();
        return entities;
    }

    protected void insertAnnotatedEntities(SessionFactory sf, int count) {
        Session session = sf.openSession();
        Transaction tx = session.beginTransaction();
        for(int i=0; i< count; i++) {
            AnnotatedEntity annotatedEntity = new AnnotatedEntity("dummy:"+i);
            session.save(annotatedEntity);
        }
        tx.commit();
        session.close();
    }

    protected void insertDummyEntities(SessionFactory sf, int count) {
        insertDummyEntities(sf, count, 0);
    }

    protected void insertDummyEntities(SessionFactory sf, int count, int childCount) {
        Session session = sf.openSession();
        Transaction tx = session.beginTransaction();
        try {
            for (int i = 0; i < count; i++) {
                DummyEntity e = new DummyEntity((long) i, "dummy:" + i, i * 123456d, new Date());
                session.save(e);
                for (int j = 0; j < childCount; j++) {
                    DummyProperty p = new DummyProperty("key:" + j, e);
                    session.save(p);
                }
            }
            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            e.printStackTrace();
        } finally {
            session.close();
        }
    }

    protected void updateDummyEntityName(SessionFactory sf, long id, String newName) {
        Session session = null;
        Transaction txn = null;
        try {
            session = sf.openSession();
            txn = session.beginTransaction();
            DummyEntity entityToUpdate = session.get(DummyEntity.class, id);
            entityToUpdate.setName(newName);
            session.update(entityToUpdate);
            txn.commit();
        } catch (RuntimeException e) {
            txn.rollback();
            e.printStackTrace();
            throw e;
        } finally {
            session.close();
        }
    }

    /**
     * Starting in Hibernate 5, it is no longer possible to set an explicit cache mode for an entity on the
     * {@code Configuration} object. The cache mode can <i>only</i> be defined in the {@code hbm.xml} file,
     * for entities configured via XML, or directly on the annotated class.
     * <p>
     * To work around that restriction, the {@code hbm.xml} files are treated as templates, with placeholders
     * for their cache modes. This method opens that template resource, replaces the cache mode and writes it
     * to a temporary file (which is marked for delete-on-exit to do housekeeping). This way, the test being
     * run can still control the caching mode.
     *
     * @param baseName the base name for the {@code hbm.xml} file to create
     */
    private File createHbmXml(final String baseName) {
        InputStream inputStream = null;
        BufferedReader reader = null;
        BufferedWriter writer = null;

        try {
            inputStream = getClass().getResourceAsStream("/hbm/" + baseName + ".xml");
            reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));

            final File hbmXmlFile = File.createTempFile(baseName, "hbm.xml");
            hbmXmlFile.deleteOnExit();

            writer = new BufferedWriter(new FileWriter(hbmXmlFile));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                writer.write(String.format(line, getCacheStrategy().getExternalName()));
                writer.newLine();
            }
            writer.flush();

            return hbmXmlFile;
        } catch (final IOException e) {
            throw new IllegalStateException("Could not prepare " + baseName + ".hbm.xml", e);
        } finally {
            IOUtil.closeResource(writer);
            IOUtil.closeResource(reader);
            IOUtil.closeResource(inputStream);
        }
    }
}
