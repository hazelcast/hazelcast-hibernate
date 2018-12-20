package com.hazelcast.hibernate;

import com.hazelcast.client.test.TestHazelcastFactory;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.hibernate.entity.AnnotatedEntity;
import com.hazelcast.hibernate.instance.HazelcastMockInstanceLoader;
import com.hazelcast.hibernate.local.LocalRegionCache;
import org.hibernate.SessionFactory;
import org.hibernate.cache.spi.UpdateTimestampsCache;
import org.hibernate.cfg.Environment;
import org.junit.After;
import org.junit.Before;

import java.util.Properties;

import static org.junit.Assert.assertEquals;

public abstract class HibernateTopicTestSupport extends HibernateTestSupport {

    protected final String CACHE_ANNOTATED_ENTITY = AnnotatedEntity.class.getName();
    protected final String CACHE_ENTITY_PROPERTIES = CACHE_ENTITY + ".properties";
    protected final String CACHE_TIMESTAMPS_REGION = UpdateTimestampsCache.class.getName();

    protected SessionFactory sf;

    private static TestHazelcastFactory factory;

    private HazelcastInstance instance;

    @Before
    public void postConstruct() {
        HazelcastMockInstanceLoader loader = new HazelcastMockInstanceLoader();
        factory = new TestHazelcastFactory();
        loader.setInstanceFactory(factory);
        sf = createSessionFactory(getCacheProperties(),  loader);

        instance = getHazelcastInstance(sf);
        new LocalRegionCache("cache", instance, null, true);
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

    protected Properties getCacheProperties() {
        Properties props = new Properties();
        props.put("TestAccessType", getCacheStrategy());
        props.setProperty(Environment.CACHE_REGION_FACTORY, HazelcastLocalCacheRegionFactory.class.getName());
        return props;
    }

    protected void assertTopicNotifications(int expectedCount, String topicName) {
        assertEquals(expectedCount, instance.getTopic(topicName).getLocalTopicStats().getPublishOperationCount());
    }
}
