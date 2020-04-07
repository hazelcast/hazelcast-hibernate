package com.hazelcast.hibernate;

import com.hazelcast.client.test.TestHazelcastFactory;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.hibernate.entity.AnnotatedEntity;
import com.hazelcast.hibernate.entity.DummyEntity;
import com.hazelcast.hibernate.instance.HazelcastMockInstanceLoader;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Environment;
import org.junit.After;
import org.junit.Before;

import java.util.ArrayList;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public abstract class HibernateTopicTestSupport extends HibernateTestSupport {

    private final TestHazelcastFactory factory = new TestHazelcastFactory();

    protected final String CACHE_ANNOTATED_ENTITY = AnnotatedEntity.class.getName();
    protected final String CACHE_ENTITY_PROPERTIES = CACHE_ENTITY + ".properties";

    protected SessionFactory sf;

    private HazelcastInstance instance;

    @Before
    public void postConstruct() {
        HazelcastMockInstanceLoader loader = new HazelcastMockInstanceLoader();
        loader.setInstanceFactory(factory);
        sf = createSessionFactory(getCacheProperties(),  loader);

        instance = getHazelcastInstance(sf);

        configureTopic(instance);
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

    protected abstract void configureTopic(HazelcastInstance instance);

    protected Properties getCacheProperties() {
        Properties props = new Properties();
        props.put("TestAccessType", getCacheStrategy());
        props.setProperty(Environment.CACHE_REGION_FACTORY, HazelcastLocalCacheRegionFactory.class.getName());
        return props;
    }

    protected abstract String getTimestampsRegionName();

    protected void insertDummyEntities(int count) {
        insertDummyEntities(sf, count, 0);
    }

    protected void insertDummyEntities(int count, int childCount) {
        insertDummyEntities(sf, count, childCount);
    }

    protected void insertAnnotatedEntities(int count) {
        insertAnnotatedEntities(sf, count);
    }

    protected ArrayList<DummyEntity> getDummyEntities(long untilId) {
        return getDummyEntities(sf, untilId);
    }

    protected void updateDummyEntityName(long id, String newName) {
        updateDummyEntityName(sf, id, newName);
    }

    protected void deleteDummyEntity(long id) throws Exception {
        deleteDummyEntity(sf, id);
    }

    protected void executeUpdateQuery(String queryString) throws RuntimeException {
        executeUpdateQuery(sf, queryString);
    }

    protected void assertTopicNotifications(int expectedCount, String topicName) {
        assertEquals(expectedCount, instance.getTopic(topicName).getLocalTopicStats().getPublishOperationCount());
    }
}
