package com.hazelcast.hibernate;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.impl.clientside.HazelcastClientProxy;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.hibernate.entity.DummyEntity;
import com.hazelcast.hibernate.instance.HazelcastAccessor;
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
import static org.junit.Assert.assertTrue;

@RunWith(HazelcastSerialClassRunner.class)
@Category(SlowTest.class)
public class NativeClientTest
        extends HibernateSlowTestSupport {

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

    @Test
    public void testNativeClient() {
        // This test has been moved from CustomPropertiesTest to here only for hazelcast-hibernate5
        // module due to MockLoader complaints. 52 and 53 modules still test the behavior under
        // CustomPropertiesTest. See the error at:
        // https://github.com/hazelcast/hazelcast-hibernate5/issues/30#issuecomment-623319465
        final HazelcastInstance clientInstance = HazelcastAccessor.getHazelcastInstance(clientSf);
        assertTrue(clientInstance instanceof HazelcastClientProxy);
        HazelcastClientProxy client = (HazelcastClientProxy) clientInstance;
        ClientConfig clientConfig = client.getClientConfig();
        assertEquals("dev-custom", clientConfig.getClusterName());
        assertTrue(clientConfig.getNetworkConfig().isSmartRouting());
        assertTrue(clientConfig.getNetworkConfig().isRedoOperation());
    }

    @Test
    public void testInsertLoad() {
        Session session = clientSf.openSession();
        Transaction tx = session.beginTransaction();
        DummyEntity e = new DummyEntity((long) 1, "dummy:1", 123456d, new Date());
        session.save(e);
        tx.commit();
        session.close();

        session = clientSf.openSession();
        DummyEntity retrieved = session.get(DummyEntity.class, (long)1);
        assertEquals("dummy:1", retrieved.getName());
    }

    @After
    public void tearDown() {
        if(clientSf !=null) {
            clientSf.close();
        }
        if(sf !=null) {
            sf.close();
        }
    }

    protected SessionFactory createClientSessionFactory(Properties props) {
        Configuration conf = new Configuration();
        conf.configure(HibernateTestSupport.class.getClassLoader().getResource("test-hibernate-client.cfg.xml"));
        addHbmMappings(conf);
        conf.addProperties(props);

        final SessionFactory sf = conf.buildSessionFactory();
        sf.getStatistics().setStatisticsEnabled(true);

        return sf;
    }
}
