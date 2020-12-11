package com.hazelcast.hibernate;

import com.hazelcast.hibernate.entity.DummyEntity;
import com.hazelcast.test.HazelcastSerialParametersRunnerFactory;
import com.hazelcast.test.annotation.SlowTest;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cache.spi.UpdateTimestampsCache;
import org.hibernate.cfg.Environment;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.internal.SessionImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.*;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
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
                new Object[]{"hazelcast-custom.xml", HazelcastLocalCacheRegionFactory.class.getName()},
                new Object[]{"hazelcast-custom-object-in-memory-format.xml", HazelcastCacheRegionFactory.class.getName()}, // fails
                new Object[]{"hazelcast-custom-object-in-memory-format.xml", HazelcastLocalCacheRegionFactory.class.getName()}
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
    public void testTimestampCacheUpdate_issue219() throws Exception {
        final int entityCount = 10;
        insertDummyEntities(entityCount);

        // Expect cache miss on sf since executing the query first time
        executeQueryAndVerifyCacheStats(sf, entityCount, 0, 1, 1);

        // Expect cache miss on sf2 since query caches are always local
        executeQueryAndVerifyCacheStats(sf2, entityCount, 0, 1, 1);

        // Invalidate cached queries on both regions
        deleteDummyEntity(sf, 0);
        deleteDummyEntity(sf2, 1);

        // Wait until invalidation messages are sent between sf and sf2
        await().atMost(10, SECONDS).until(() -> isTimestampCacheUpToDate(sf) && isTimestampCacheUpToDate(sf2));

        // Expect the cached query on sf is invalidated and the new one is inserted
        executeQueryAndVerifyCacheStats(sf, entityCount-2, 0, 2, 2);

        // Expect the cached query on sf2 is invalidated and the new one is inserted
        executeQueryAndVerifyCacheStats(sf2, entityCount-2, 0, 2, 2);

        // Verify sf query cache is not blocked
        executeQueryAndVerifyCacheStats(sf, entityCount-2, 1, 2, 2);

        // Verify sf2 query cache is not blocked
        executeQueryAndVerifyCacheStats(sf2, entityCount-2, 1, 2, 2);
    }

    /**
     * Runs #executeCacheableQuery on the factory and verifies the query result
     * as well as the query cache stats after the query is executed.
     *
     * @param factory  factory to run the query
     * @param expectedEntryCount  expected number of entries returned by the query
     * @param expectedCacheHits  expected number of QueryCacheHitCount
     * @param expectedCacheMisses  expected number of QueryCacheMissCount
     * @param expectedCachePuts  expected number of QueryCachePutCount
     */
    private void executeQueryAndVerifyCacheStats(SessionFactory factory, int expectedEntryCount,
                                                 long expectedCacheHits, long expectedCacheMisses,
                                                 long expectedCachePuts) {
        List<DummyEntity> list = executeCacheableQuery(factory);
        assertEquals(expectedEntryCount, list.size());
        assertEquals(expectedCacheHits, factory.getStatistics().getQueryCacheHitCount());
        assertEquals(expectedCacheMisses, factory.getStatistics().getQueryCacheMissCount());
        assertEquals(expectedCachePuts, factory.getStatistics().getQueryCachePutCount());
    }

    /**
     * Executes a query to select all DummyEntity entries and caches the result.
     *
     * @param sessionFactory  factory to run the query
     * @return  query result
     */
    private List<DummyEntity> executeCacheableQuery(SessionFactory sessionFactory) {
        try (Session session = sessionFactory.openSession()) {
            Query query = session.createQuery("from " + DummyEntity.class.getName());
            query.setCacheable(true);
            return query.list();
        }
    }

    private boolean isTimestampCacheUpToDate(SessionFactory sessionFactory) {
        try (Session session = sessionFactory.openSession()) {
            UpdateTimestampsCache timestampsCache = ((SessionFactoryImpl) sessionFactory).getUpdateTimestampsCache();
            return timestampsCache.isUpToDate(new HashSet<>(Arrays.asList("dummy_entities")),
                    timestampsCache.getRegion().nextTimestamp(), (SessionImpl) session);
        }
    }

}
