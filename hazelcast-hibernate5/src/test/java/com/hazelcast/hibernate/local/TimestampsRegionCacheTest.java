package com.hazelcast.hibernate.local;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
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
import com.hazelcast.core.Cluster;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ITopic;
import com.hazelcast.core.Member;
import com.hazelcast.core.Message;
import com.hazelcast.core.MessageListener;

import java.util.UUID;

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
    public void shouldUpdateTimestampAfterPreInvalidationMessage_issue_33() {
        long firstTimestamp = 1;
        long secondTimestamp = 2;
        long preInvalidationPublishTime = 3;
        long thirdTimestamp = 4;
        long invalidationPublishTime = 5;
        long afterUpdateReadTime = 6;

        when(cluster.getClusterTime()).thenReturn(firstTimestamp, secondTimestamp, thirdTimestamp);

        assertThat(target.put("Entity", firstTimestamp, firstTimestamp, null), is(true));
        assertThat("primed value should be in the cache", (Long) target.get("Entity",
                firstTimestamp), is(firstTimestamp));

        // During an update of an entity <E>, two calls are made to update-timestamps-cache such
        // that the first call updates the timestamp for <E> about an hour offset. This is because
        // during a transaction happening for table <E>, no cached entry for <E> must be served
        // from the cache. This is simply a temporary invalidation mechanism for cached entries.
        // When the transaction ends, the second update to timestamps-cache is made with the recent
        // timestamp.

        long invalidationOffset = 100L;

        UUID regionUuid = UUID.randomUUID();

        Message<Object> preInvalidate = new Message<Object>("topicName",
                new Timestamp("Entity", secondTimestamp + invalidationOffset, regionUuid),
                preInvalidationPublishTime, member);


        // process the pre invalidation update.
        listener.onMessage(preInvalidate);

        // It's set to update local timestamp with cluster time when a message is received over
        // topic no matter what the received timestamp value is. This is preferred in favor of
        // query cache functionality. However, the reliable way would be updating the timestamp
        // with a large value to prevent serving cached entries for <E> between preInvalidation
        // and invalidation calls.
        assertTrue("Timestamp cache must be updated by preInvalidation.",
                (Long) target.get("Entity", afterUpdateReadTime) == secondTimestamp);

        Message<Object> invalidate = new Message<Object>("topicName",
                new Timestamp("Entity", thirdTimestamp, regionUuid),
                invalidationPublishTime, member);

        // process the invalidation update.
        listener.onMessage(invalidate);

        // Cache must be updated with the invalidation call. If it is ignored due to having a
        // smaller timestamp than the preInvalidation call timestamp, this will make cache
        // unusable until invalidationOffset expires.
        assertTrue("Timestamp cache must be updated after preInvalidation",
                (Long) target.get("Entity", afterUpdateReadTime) < invalidationOffset);
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
        Message<Object> message = new Message<Object>("topicName", new Timestamp("QuerySpace", secondTimestamp, UUID.randomUUID()), publishTime, member);

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
