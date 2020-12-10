package com.hazelcast.hibernate;

import com.hazelcast.hibernate.entity.DummyEntity;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.HazelcastSerialParametersRunnerFactory;
import com.hazelcast.test.annotation.SlowTest;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Environment;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(HazelcastSerialParametersRunnerFactory.class)
@Category(SlowTest.class)
public class UpdateTimestampCacheTest extends HibernateSlowTestSupport {

    @Parameterized.Parameter
    public String configFile;

    @Parameterized.Parameter(1)
    public String regionFactory;

    @Parameterized.Parameters(name = "Executing: {0}, {1}")
    public static Collection<Object[]> parameters() {
        return Arrays.asList(
                new Object[]{"hazelcast-custom.xml", HazelcastCacheRegionFactory.class.getName()},
                new Object[]{"hazelcast-custom.xml", HazelcastLocalCacheRegionFactory.class.getName()}, // fails
                new Object[]{"hazelcast-custom-object-in-memory-format.xml", HazelcastCacheRegionFactory.class.getName()}, // fails
                new Object[]{"hazelcast-custom-object-in-memory-format.xml", HazelcastLocalCacheRegionFactory.class.getName()} // fails
        );
    }

    @Override
    protected Properties getCacheProperties() {
        Properties props = new Properties();
        props.setProperty(Environment.CACHE_REGION_FACTORY, regionFactory);
        props.setProperty(CacheEnvironment.CONFIG_FILE_PATH, configFile);
        return props;
    }

    @Before
    @Override
    public void postConstruct() {
        sf = createSessionFactory(getCacheProperties(), null);
        sf2 = createSessionFactory(getCacheProperties(), null);
    }

    @Test
    public void testTimestampCacheUpdate() {
        // given
        final int entityCount = 10;
        insertDummyEntities(entityCount);

        // when
        List<DummyEntity> list = executeCacheableQuery(sf);
        // then - put query result to sf local query cache
        assertEquals(entityCount, list.size());
        verifyQueryCacheStats(sf, 0, 1, 1);

        // when
        list = executeCacheableQuery(sf2);
        // then - expect miss since query caches are always local
        assertEquals(entityCount, list.size());
        verifyQueryCacheStats(sf2, 0, 1, 1);

        // when - invalidate cached queries on both regions
        DummyEntity toDelete = list.get(0);
        Session session = sf.openSession();
        Transaction tx = session.beginTransaction();
        try {
            session.delete(toDelete);
            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            e.printStackTrace();
        } finally {
            session.close();
        }
        list = executeCacheableQuery(sf2);
        // then - verify invalidated query on sf2
        assertEquals(entityCount - 1, list.size());
        verifyQueryCacheStats(sf2, 0, 2, 2);

        // when
        list = executeCacheableQuery(sf2);
        // then - verify query cache is not blocked
        assertEquals(entityCount - 1, list.size());
        verifyQueryCacheStats(sf2, 1, 2, 2);
    }

    private List<DummyEntity> executeCacheableQuery(SessionFactory sessionFactory) {
        try (Session session = sessionFactory.openSession()) {
            Query query = session.createQuery("from " + DummyEntity.class.getName());
            query.setCacheable(true);
            return query.list();
        }
    }

    private void verifyQueryCacheStats(SessionFactory factory, long expectHits, long expectMisses, long expectPuts) {
        assertEquals(expectHits, factory.getStatistics().getQueryCacheHitCount());
        assertEquals(expectMisses, factory.getStatistics().getQueryCacheMissCount());
        assertEquals(expectPuts, factory.getStatistics().getQueryCachePutCount());
    }

}
