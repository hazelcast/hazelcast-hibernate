package com.hazelcast.hibernate;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.hibernate.entity.DummyEntity;
import com.hazelcast.instance.impl.TestUtil;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.SlowTest;
import com.hazelcast.topic.impl.TopicService;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.cfg.Environment;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(HazelcastSerialClassRunner.class)
@Category(SlowTest.class)
public class LocalRegionFactorySlowTest extends HibernateSlowTestSupport {

    @Test
    public void cleanUpRegisteredTopicOnRegionShutdown() {
        HazelcastInstance sf1Instance = HazelcastAccessor.getHazelcastInstance(sf);
        List<String> regions = new ArrayList<>(Arrays.asList(sf.getStatistics().getSecondLevelCacheRegionNames()));
        // 'Query' cache is not subscribed to a topic in 5 and 52.
        regions.removeIf(n -> n.contains("Query"));

        Function<String, Integer> getRegistrationsCount = r -> TestUtil.getNode(sf1Instance).getNodeEngine()
                .getEventService().getRegistrations(TopicService.SERVICE_NAME, r).size();

        regions.forEach(r ->
                assertEquals(r,2L, getRegistrationsCount.apply(r).longValue()));

        sf2.close();

        regions.forEach(r ->
                assertEquals(r,1L, getRegistrationsCount.apply(r).longValue()));
    }

    @Test
    public void test_query_with_non_mock_network() {
        final int entityCount = 10;
        final int queryCount = 2;
        insertDummyEntities(entityCount);
        List<DummyEntity> list = null;
        for (int i = 0; i < queryCount; i++) {
            list = executeQuery(sf);
            assertEquals(entityCount, list.size());
        }
        for (int i = 0; i < queryCount; i++) {
            list = executeQuery(sf2);
            assertEquals(entityCount, list.size());
        }

        assertNotNull(list);
        DummyEntity toDelete = list.get(0);
        Session session = sf2.openSession();
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
        assertEquals(entityCount - 1, executeQuery(sf).size());
        assertEquals(entityCount - 1, executeQuery(sf2).size());

    }

    @Override
    protected Properties getCacheProperties() {
        Properties props = new Properties();
        props.setProperty(Environment.CACHE_REGION_FACTORY, HazelcastLocalCacheRegionFactory.class.getName());
        return props;
    }
}
