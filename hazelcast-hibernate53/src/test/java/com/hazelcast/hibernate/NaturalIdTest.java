package com.hazelcast.hibernate;

import com.hazelcast.hibernate.entity.AnnotatedEntity;
import com.hazelcast.test.HazelcastSerialParametersRunnerFactory;
import com.hazelcast.test.annotation.SlowTest;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cfg.Environment;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(HazelcastSerialParametersRunnerFactory.class)
@Category(SlowTest.class)
public class NaturalIdTest extends HibernateStatisticsTestSupport {

    @Parameterized.Parameters(name = "Executing: {0}")
    public static Collection<Object[]> parameters() {
        return Arrays.asList(new Object[]{AccessType.READ_WRITE},
                new Object[]{AccessType.READ_ONLY},
                new Object[]{AccessType.NONSTRICT_READ_WRITE}
        );
    }

    @Parameterized.Parameter(0)
    public AccessType defaultAccessType;

    @Override
    protected Properties getCacheProperties() {
        Properties props = new Properties();
        props.put("TestAccessType", defaultAccessType);
        props.setProperty(Environment.CACHE_REGION_FACTORY, InternalMockRegionFactory.class.getName());
        return props;
    }

    @SuppressWarnings("Duplicates")
    @Test
    public void testNaturalIdCacheEvictsEntityOnUpdate() {
        Assume.assumeTrue(defaultAccessType == AccessType.READ_WRITE);

        insertAnnotatedEntities(1);

        Session session = sf.openSession();
        Transaction tx = session.beginTransaction();

        //1 cache hit since dummy:0 just inserted and still in the cache
        AnnotatedEntity toBeUpdated = session.byNaturalId(AnnotatedEntity.class).using("title", "dummy:0").getReference();
        toBeUpdated.setTitle("dummy101");
        tx.commit();
        session.close();

        assertEquals(1, sf.getStatistics().getNaturalIdCacheHitCount());

        //dummy:0 should be evicted and this leads to a cache miss
        session = sf.openSession();

        session.byNaturalId(AnnotatedEntity.class)
                .using("title", "dummy:0")
                .load();

        assertEquals(1, sf.getStatistics().getNaturalIdCacheMissCount());
    }

    @SuppressWarnings("Duplicates")
    @Test
    public void testNaturalIdCacheStillHitsAfterIrrelevantNaturalIdUpdate() {
        Assume.assumeTrue(defaultAccessType == AccessType.READ_WRITE);

        insertAnnotatedEntities(2);

        Session session = sf.openSession();
        Transaction tx = session.beginTransaction();

        //1 cache hit since dummy:1 just inserted and still in the cache
        AnnotatedEntity toBeUpdated = session.byNaturalId(AnnotatedEntity.class).using("title", "dummy:1").getReference();
        toBeUpdated.setTitle("dummy101");
        tx.commit();
        session.close();

        assertEquals(1, sf.getStatistics().getNaturalIdCacheHitCount());

        //only dummy:1 should be evicted from cache on contrary to behavior of hibernate query cache without natural ids
        session = sf.openSession();

        session.byNaturalId(AnnotatedEntity.class)
                .using("title", "dummy:0")
                .load();

        //cache hit dummy:0 + previous hit
        assertEquals(2, sf.getStatistics().getNaturalIdCacheHitCount());
    }

    @SuppressWarnings("Duplicates")
    @Test
    public void testFindByNaturalId() {
        insertAnnotatedEntities(1);
        Session session = sf.openSession();

        AnnotatedEntity toBeUpdated = session.byNaturalId(AnnotatedEntity.class).using("title", "dummy:0").getReference();
        assertEquals("dummy:0", toBeUpdated.getTitle());
        session.close();
    }

    @Test
    public void testEvictionNaturalId() {
        insertAnnotatedEntities(1);
        sf.getCache().evictNaturalIdData(AnnotatedEntity.class);
        assertFalse(sf.getCache().containsEntity(AnnotatedEntity.class, 0L));
    }

    public static class InternalMockRegionFactory
            extends HazelcastCacheRegionFactory {

        private AccessType defaultAccessType;

        public InternalMockRegionFactory() {
            super();
        }

        public InternalMockRegionFactory(final Properties properties) {
            defaultAccessType = (AccessType) properties.get("TestAccessType");
        }

        @Override
        public AccessType getDefaultAccessType() {
            return defaultAccessType;
        }
    }
}
