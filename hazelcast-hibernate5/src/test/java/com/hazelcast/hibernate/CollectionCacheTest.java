package com.hazelcast.hibernate;

import com.hazelcast.client.test.TestHazelcastFactory;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.hibernate.entity.DummyEntity;
import com.hazelcast.hibernate.entity.DummyProperty;
import com.hazelcast.hibernate.instance.HazelcastMockInstanceLoader;
import com.hazelcast.test.HazelcastSerialParametersRunnerFactory;
import com.hazelcast.test.annotation.SlowTest;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Environment;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(HazelcastSerialParametersRunnerFactory.class)
@Category(SlowTest.class)
public class CollectionCacheTest extends HibernateTestSupport {

    @Parameterized.Parameter(0)
    public String cacheRegionFactory;

    private TestHazelcastFactory factory;
    private SessionFactory sf;

    @Parameterized.Parameters(name = "Executing: {0}")
    public static Collection<Object[]> parameters() {
        return Arrays.asList(new Object[]{HazelcastLocalCacheRegionFactory.class.getName()},
                new Object[]{HazelcastCacheRegionFactory.class.getName()}
        );
    }

    @Before
    public void postConstruct() {
        HazelcastMockInstanceLoader loader = new HazelcastMockInstanceLoader();
        factory = new TestHazelcastFactory();
        loader.setInstanceFactory(factory);
        sf = createSessionFactory(getCacheProperties(),  loader);
    }

    @After
    public void preDestroy() {
        if (sf != null) {
            sf.close();
            sf = null;
        }
        Hazelcast.shutdownAll();
        factory.shutdownAll();
    }

    @Test
    public void testReplaceCollection() throws Exception {
        Session session = sf.openSession();
        Transaction transaction = session.beginTransaction();

        DummyProperty someProp = new DummyProperty("some prop");
        session.save(someProp);

        DummyEntity entity = new DummyEntity();
        entity.setId(1L);
        entity.setName("some entity");
        entity.setValue(27.0);
        entity.setDate(new Date(System.currentTimeMillis()));
        entity.setProperties(new HashSet<DummyProperty>(Collections.singletonList(someProp)));
        session.save(entity);

        transaction.commit();
        session.close();

        session = sf.openSession();
        transaction = session.beginTransaction();

        DummyProperty property = new DummyProperty("somekey");
        session.save(property);

        entity = (DummyEntity) session.get(DummyEntity.class, 1L);
        HashSet<DummyProperty> updatedProperties = new HashSet<DummyProperty>();
        updatedProperties.add(property);
        entity.setProperties(updatedProperties);

        session.update(entity);

        transaction.commit();
        session.close();

        session = sf.openSession();

        entity = (DummyEntity) session.load(DummyEntity.class, entity.getId());
        // just for making Hibernate load the object
        assertEquals("some entity", entity.getName());

        session.close();
    }

    private Properties getCacheProperties() {
        Properties props = new Properties();
        props.setProperty(Environment.CACHE_REGION_FACTORY, cacheRegionFactory);
        return props;
    }
}
