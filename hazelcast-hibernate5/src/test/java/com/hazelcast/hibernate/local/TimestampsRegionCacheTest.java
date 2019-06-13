package com.hazelcast.hibernate.local;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.cluster.Cluster;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.topic.ITopic;
import com.hazelcast.core.Member;
import com.hazelcast.topic.Message;
import com.hazelcast.topic.MessageListener;

@RunWith(MockitoJUnitRunner.class)
public class TimestampsRegionCacheTest {

    private static final String CACHE_NAME = "cache";

    @Mock private Config config;
    @Mock private MapConfig mapConfig;
    @Mock private ITopic<Object> topic;
    @Mock private HazelcastInstance instance;
    @Mock private Cluster cluster;
    @Mock private Member member;

    private TimestampsRegionCache target;
    private MessageListener<Object> listener;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Before
    public void setup() {
        when(config.findMapConfig(eq(CACHE_NAME))).thenReturn(mapConfig);
        when(instance.getCluster()).thenReturn(cluster);
        when(instance.getConfig()).thenReturn(config);
        when(instance.getTopic(eq(CACHE_NAME))).thenReturn(topic);

        // make the message appear that it is coming from a different member of the cluster
        when(member.localMember()).thenReturn(false);

        ArgumentCaptor<MessageListener> listener = ArgumentCaptor.forClass(MessageListener.class);
        when(topic.addMessageListener(listener.capture())).thenReturn("ignored");
        target = new TimestampsRegionCache(CACHE_NAME, instance);
        this.listener = listener.getValue();
    }

    @Test
    public void shouldUseClusterTimestampFromInvalidationmessageInsteadOfSystemTime() {
        long firstTimestamp = 1;
        long secondTimestamp = 2;
        long publishTime = 3;
        long clusterTime = 4;

        when(cluster.getClusterTime()).thenReturn(firstTimestamp, secondTimestamp);

        // cache is primed by call, that uses clusterTime instead of system clock for timestamp
        assertThat(target.put("QuerySpace", firstTimestamp, firstTimestamp, null), is(true));

        assertThat("primed value should be in the cache", (Long)target.get("QuerySpace", firstTimestamp), is(firstTimestamp));

        // a message is generated on a different cluster member informing us to update the timestamp region cache
        Message<Object> message = new Message<Object>("topicName", new Timestamp("QuerySpace", secondTimestamp), publishTime, member);

        // process the timestamp region update
        listener.onMessage(message);

        // this fails if we use system time instead of cluster time, causing the value to stay invisible until clustertime == system time (which it often isn't)
        assertThat("key should be visible and have value specified in timestamp message, with current cluster time.", (Long)target.get("QuerySpace", clusterTime), is(secondTimestamp));
    }

    @Test
    public void clearCache() {
        long aTimestamp = 1;
        assertThat(target.put("QuerySpace", aTimestamp, aTimestamp, null), is(true));
        assertThat("value should be in the cache", (Long)target.get("QuerySpace", aTimestamp), is(aTimestamp));

        target.clear();

        assertThat("value should not be in the cache", target.get("QuerySpace", aTimestamp), nullValue());
    }    
}
