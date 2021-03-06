package com.hazelcast.hibernate;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.hibernate.entity.DummyEntity;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.SlowTest;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.Date;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

@RunWith(HazelcastSerialClassRunner.class)
@Category(SlowTest.class)
public class NativeClientFailoverTest extends HibernateSlowTestSupport {

    protected SessionFactory clientSf;

    @Override
    protected Properties getCacheProperties() {
        Properties props = new Properties();
        props.setProperty(Environment.CACHE_REGION_FACTORY, HazelcastCacheRegionFactory.class.getName());
        return props;
    }

    @Before
    @Override
    public void postConstruct() {
        sf = createSessionFactory(getCacheProperties(), null);
        clientSf = createClientSessionFactory(getCacheProperties());
    }

    @Test(timeout = 5000)
    public void shouldBypassCacheWhenClientDisconnected() {
        Session session = clientSf.openSession();

        Hazelcast.shutdownAll();

        Transaction tx = session.beginTransaction();
        DummyEntity e = new DummyEntity(1, "dummy:1", 123456d, new Date());
        session.save(e);
        tx.commit();
        session.close();

        session = clientSf.openSession();
        DummyEntity retrieved = session.get(DummyEntity.class, (long) 1);
        assertEquals("dummy:1", retrieved.getName());
    }

    @After
    public void tearDown() {
        if (clientSf != null) {
            clientSf.close();
        }
    }

    protected SessionFactory createClientSessionFactory(Properties props) {
        Configuration conf = new Configuration();
        conf.configure(HibernateTestSupport.class.getClassLoader().getResource("test-hibernate-client.cfg.xml"));
        addHbmMappings(conf);
        conf.addProperties(props);

        return conf.buildSessionFactory();
    }
}
