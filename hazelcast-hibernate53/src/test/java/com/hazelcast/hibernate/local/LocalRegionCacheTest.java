package com.hazelcast.hibernate.local;

import com.hazelcast.client.impl.clientside.HazelcastClientProxy;
import com.hazelcast.cluster.Cluster;
import com.hazelcast.cluster.Member;
import com.hazelcast.config.Config;
import com.hazelcast.config.EvictionConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MaxSizePolicy;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.hibernate.CacheEnvironment;
import com.hazelcast.map.impl.eviction.ZeroMemoryInfoAccessor;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.QuickTest;
import com.hazelcast.topic.ITopic;
import com.hazelcast.topic.Message;
import com.hazelcast.topic.MessageListener;
import org.hibernate.cache.cfg.spi.CollectionDataCachingConfig;
import org.hibernate.cache.cfg.spi.DomainDataRegionConfig;
import org.hibernate.cache.cfg.spi.EntityDataCachingConfig;
import org.hibernate.cache.spi.RegionFactory;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import static com.hazelcast.config.MapConfig.DEFAULT_MAX_SIZE_POLICY;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(HazelcastSerialClassRunner.class)
@Category(QuickTest.class)
@SuppressWarnings("unchecked")
public class LocalRegionCacheTest {

    private static final String CACHE_NAME = "cache";

    @Mock
    private RegionFactory regionFactory;

    @Test
    public void testConstructionIgnoresUnsupportedOperationExceptionsFromConfig() {
        HazelcastInstance instance = mock(HazelcastInstance.class);
        doThrow(UnsupportedOperationException.class).when(instance).getConfig();

        LocalRegionCache localRegionCache = LocalRegionCache.builder().withRegionFactory(regionFactory)
                .withName(CACHE_NAME)
                .withHazelcastInstance(instance)
                .withTopic(false)
                .build();

        assertThat(localRegionCache).isNotNull();
    }

    @Test
    public void testConstructionIgnoresVersionComparatorForUnversionedEntityData() {
        DomainDataRegionConfig domainDataRegionConfig = mock(DomainDataRegionConfig.class);
        EntityDataCachingConfig entityDataCachingConfig = mock(EntityDataCachingConfig.class);
        when(domainDataRegionConfig.getEntityCaching()).thenReturn(Collections.singletonList(entityDataCachingConfig));
        doThrow(AssertionError.class).when(entityDataCachingConfig).getVersionComparatorAccess(); // Will fail the test if called

        LocalRegionCache localRegionCache = LocalRegionCache.builder().withRegionFactory(regionFactory)
                .withName(CACHE_NAME)
                .withRegionConfig(domainDataRegionConfig)
                .withTopic(false)
                .build();

        assertThat(localRegionCache).isNotNull();
        verify(entityDataCachingConfig).isVersioned(); // Verify that the versioned flag was checked
        verifyNoMoreInteractions(entityDataCachingConfig);
    }

    @Test
    public void testConstructorSetsVersionComparatorForVersionedEntityData() {
        Comparator<?> comparator = mock(Comparator.class);

        DomainDataRegionConfig domainDataRegionConfig = mock(DomainDataRegionConfig.class);
        EntityDataCachingConfig entityDataCachingConfig = mock(EntityDataCachingConfig.class);
        when(domainDataRegionConfig.getEntityCaching()).thenReturn(Collections.singletonList(entityDataCachingConfig));
        when(entityDataCachingConfig.isVersioned()).thenReturn(true);
        when(entityDataCachingConfig.getVersionComparatorAccess()).thenReturn((Supplier) () -> comparator);

        LocalRegionCache localRegionCache = LocalRegionCache.builder().withRegionFactory(regionFactory)
                .withName(CACHE_NAME)
                .withRegionConfig(domainDataRegionConfig)
                .withTopic(false)
                .build();

        assertThat(localRegionCache).isNotNull();
        verify(entityDataCachingConfig).isVersioned();
        verify(entityDataCachingConfig).getVersionComparatorAccess();
    }

    @Test
    public void testConstructionIgnoresVersionComparatorForUnversionedCollectionData() {
        DomainDataRegionConfig domainDataRegionConfig = mock(DomainDataRegionConfig.class);
        CollectionDataCachingConfig collectionDataCachingConfig = mock(CollectionDataCachingConfig.class);
        when(domainDataRegionConfig.getCollectionCaching()).thenReturn(Collections.singletonList(collectionDataCachingConfig));
        doThrow(AssertionError.class).when(collectionDataCachingConfig).getOwnerVersionComparator(); // Will fail the test if called

        LocalRegionCache.builder().withRegionFactory(regionFactory)
                .withName(CACHE_NAME)
                .withRegionConfig(domainDataRegionConfig)
                .withTopic(false)
                .build();
        verify(collectionDataCachingConfig).isVersioned(); // Verify that the versioned flag was checked
        verifyNoMoreInteractions(collectionDataCachingConfig);
    }

    @Test
    public void testConstructionSetsVersionComparatorForVersionedCollectionData() {
        Comparator<?> comparator = mock(Comparator.class);

        DomainDataRegionConfig domainDataRegionConfig = mock(DomainDataRegionConfig.class);
        CollectionDataCachingConfig collectionDataCachingConfig = mock(CollectionDataCachingConfig.class);
        when(domainDataRegionConfig.getCollectionCaching()).thenReturn(Collections.singletonList(collectionDataCachingConfig));
        when(collectionDataCachingConfig.isVersioned()).thenReturn(true);
        when(collectionDataCachingConfig.getOwnerVersionComparator()).thenReturn(comparator);

        LocalRegionCache localRegionCache = LocalRegionCache.builder().withRegionFactory(regionFactory)
                .withName(CACHE_NAME)
                .withRegionConfig(domainDataRegionConfig)
                .withTopic(false)
                .build();

        assertThat(localRegionCache).isNotNull();
        verify(collectionDataCachingConfig).getOwnerVersionComparator();
        verify(collectionDataCachingConfig).isVersioned();
    }

    @Test
    public void testConstructionDoesNotRegisterTopicListenerIfNotRequested() {
        MapConfig mapConfig = someMapConfigWithEvictionConfig();

        Config config = mock(Config.class);
        when(config.findMapConfig(eq(CACHE_NAME))).thenReturn(mapConfig);

        HazelcastInstance instance = mock(HazelcastInstance.class);
        when(instance.getConfig()).thenReturn(config);

        LocalRegionCache localRegionCache = LocalRegionCache.builder().withRegionFactory(regionFactory)
                .withName(CACHE_NAME)
                .withHazelcastInstance(instance)
                .withTopic(false)
                .build();

        assertThat(localRegionCache).isNotNull();
        verify(config).findMapConfig(eq(CACHE_NAME));
        verify(instance).getConfig();
        verify(instance, never()).getTopic(anyString());
    }

    @Test
    public void test_EvictionConfig_from_HazelcastInstance() {
        MapConfig mapConfig = someMapConfigWithEvictionConfig();

        Config config = mock(Config.class);
        when(config.findMapConfig(eq(CACHE_NAME))).thenReturn(mapConfig);

        EvictionConfig maxSizeConfig = mapConfig.getEvictionConfig();

        HazelcastInstance instance = mock(HazelcastInstance.class);
        when(instance.getConfig()).thenReturn(config);

        LocalRegionCache.builder().withRegionFactory(regionFactory)
                .withName(CACHE_NAME)
                .withHazelcastInstance(instance)
                .withTopic(false)
                .build();
        verify(maxSizeConfig, atLeastOnce()).getSize();
        verify(maxSizeConfig, atLeastOnce()).getMaxSizePolicy();
        verify(mapConfig, atLeastOnce()).getTimeToLiveSeconds();
    }

    @Test
    public void test_EvictionConfig_creation_from_MapConfig() {
        MapConfig mapConfig = someMapConfigWithEvictionConfig(234, MaxSizePolicy.PER_NODE);

        LocalRegionCache.EvictionConfig evictionConfig = LocalRegionCache.EvictionConfig.create(mapConfig);

        assertThat(evictionConfig.getTimeToLive()).hasSeconds(123);
        assertThat(evictionConfig.getSize()).isEqualTo(234);
        assertThat(evictionConfig.getMaxSizePolicy()).isEqualTo(MaxSizePolicy.PER_NODE);
    }

    @Test
    public void test_freeHeapCacheEvictor_started_when_FREE_HEAP_SIZE_policy() {
        MapConfig mapConfig = someMapConfigWithEvictionConfig(234, MaxSizePolicy.FREE_HEAP_SIZE);

        Config config = mock(Config.class);
        when(config.findMapConfig(eq(CACHE_NAME))).thenReturn(mapConfig);

        HazelcastInstance instance = mock(HazelcastInstance.class);
        when(instance.getConfig()).thenReturn(config);

        FreeHeapBasedCacheEvictor freeHeapBasedCacheEvictor = spy(new FreeHeapBasedCacheEvictor(newSingleThreadScheduledExecutor(),
                new ZeroMemoryInfoAccessor(), Duration.ofMillis(50)));

        LocalRegionCache.builder().withRegionFactory(regionFactory)
                .withName(CACHE_NAME)
                .withHazelcastInstance(instance)
                .withTopic(false)
                .withFreeHeapBasedCacheEvictor(freeHeapBasedCacheEvictor)
                .build();

        verify(freeHeapBasedCacheEvictor).start(eq(CACHE_NAME), any(), eq(234L * 1024 * 1024));
    }

    @Test
    public void test_freeHeapCacheEvictor_is_required_for_FREE_HEAP_SIZE_policy() {
        MapConfig mapConfig = someMapConfigWithEvictionConfig(234, MaxSizePolicy.FREE_HEAP_SIZE);

        Config config = mock(Config.class);
        when(config.findMapConfig(eq(CACHE_NAME))).thenReturn(mapConfig);

        HazelcastInstance instance = mock(HazelcastInstance.class);
        when(instance.getConfig()).thenReturn(config);

        assertThatThrownBy(() -> LocalRegionCache.builder().withRegionFactory(regionFactory)
                .withName(CACHE_NAME)
                .withHazelcastInstance(instance)
                .withTopic(false)
                .build()
        ).isInstanceOf(IllegalStateException.class)
                .hasMessage("FreeHeapBasedCacheEvictor is required for FREE_HEAP_SIZE policy");
    }

    @Test
    public void test_freeHeapCacheEvictor_NOT_started_when_PER_NODE_policy() {
        MapConfig mapConfig = someMapConfigWithEvictionConfig(234, MaxSizePolicy.PER_NODE);

        Config config = mock(Config.class);
        when(config.findMapConfig(eq(CACHE_NAME))).thenReturn(mapConfig);

        HazelcastInstance instance = mock(HazelcastInstance.class);
        when(instance.getConfig()).thenReturn(config);

        FreeHeapBasedCacheEvictor freeHeapBasedCacheEvictor = spy(new FreeHeapBasedCacheEvictor(mock(ScheduledExecutorService.class),
                new ZeroMemoryInfoAccessor(), Duration.ofMillis(50)));

        LocalRegionCache.builder().withRegionFactory(regionFactory)
                .withName(CACHE_NAME)
                .withHazelcastInstance(instance)
                .withTopic(false)
                .withFreeHeapBasedCacheEvictor(freeHeapBasedCacheEvictor)
                .build();
        verifyNoInteractions(freeHeapBasedCacheEvictor);
    }

    @Test
    public void test_free_heap_evictor_should_remove_task_when_cache_destroyed() {
        MapConfig mapConfig = someMapConfigWithEvictionConfig(234, MaxSizePolicy.FREE_HEAP_SIZE);

        Config config = mock(Config.class);
        when(config.findMapConfig(eq(CACHE_NAME))).thenReturn(mapConfig);

        HazelcastInstance instance = mock(HazelcastInstance.class);
        when(instance.getConfig()).thenReturn(config);

        FreeHeapBasedCacheEvictor freeHeapBasedCacheEvictor = spy(new FreeHeapBasedCacheEvictor());
        LocalRegionCache localRegionCache = LocalRegionCache.builder().withRegionFactory(regionFactory)
                .withName(CACHE_NAME)
                .withHazelcastInstance(instance)
                .withTopic(false)
                .withFreeHeapBasedCacheEvictor(freeHeapBasedCacheEvictor)
                .build();
        localRegionCache.destroy();

        verify(freeHeapBasedCacheEvictor).stop(CACHE_NAME);
    }


    private MapConfig someMapConfigWithEvictionConfig() {
        return someMapConfigWithEvictionConfig(234, MaxSizePolicy.PER_NODE);
    }

    private MapConfig someMapConfigWithEvictionConfig(int sizeInMB, MaxSizePolicy maxSizePolicy) {
        return spy(new MapConfig()
                .setTimeToLiveSeconds(123)
                .setEvictionConfig(spy(new EvictionConfig()
                        .setSize(sizeInMB)
                        .setMaxSizePolicy(maxSizePolicy))
                ));
    }

    @Test
    public void test_EvictionConfig_creation_withoutMapConfig() {
        LocalRegionCache.EvictionConfig evictionConfig = LocalRegionCache.EvictionConfig.create(null);

        assertThat(evictionConfig.getTimeToLive()).hasMillis(CacheEnvironment.getDefaultCacheTimeoutInMillis());
        assertThat(evictionConfig.getSize()).isEqualTo(100_000);
        assertThat(evictionConfig.getMaxSizePolicy()).isEqualTo(DEFAULT_MAX_SIZE_POLICY);
    }


    @Test
    public void test_passing_evictionConfig_when_no_instance() {
        LocalRegionCache.EvictionConfig evictionConfig = spy(LocalRegionCache.EvictionConfig.create(someMapConfigWithEvictionConfig()));

        LocalRegionCache.builder().withRegionFactory(regionFactory)
                .withName(CACHE_NAME)
                .withTopic(false)
                .withEvictionConfig(evictionConfig)
                .build();

        verify(evictionConfig, atLeastOnce()).getSize();
        verify(evictionConfig, atLeastOnce()).getTimeToLive();
        verify(evictionConfig, atLeastOnce()).getMaxSizePolicy();
    }

    @Test
    public void testRegistrationTopicListener() {
        MapConfig mapConfig = someMapConfigWithEvictionConfig();

        Config config = mock(Config.class);
        when(config.findMapConfig(eq(CACHE_NAME))).thenReturn(mapConfig);

        ITopic<Object> topic = mock(ITopic.class);
        when(topic.addMessageListener(isNotNull())).thenReturn(UUID.randomUUID());

        HazelcastInstance instance = mock(HazelcastInstance.class);
        when(instance.getConfig()).thenReturn(config);
        when(instance.getTopic(eq(CACHE_NAME))).thenReturn(topic);

        LocalRegionCache.builder().withRegionFactory(regionFactory)
                .withName(CACHE_NAME)
                .withHazelcastInstance(instance)
                .withTopic(true)
                .build();
        verify(config).findMapConfig(eq(CACHE_NAME));
        verify(instance).getConfig();
        verify(instance).getTopic(eq(CACHE_NAME));
        verify(topic).addMessageListener(isNotNull());
    }

    @Test
    public void testMessagesFromLocalNodeAreIgnored() {
        MapConfig mapConfig = mock(MapConfig.class);

        LocalRegionCache.EvictionConfig evictionConfig = spy(LocalRegionCache.EvictionConfig.create(someMapConfigWithEvictionConfig()));

        Config config = mock(Config.class);
        when(config.findMapConfig(eq(CACHE_NAME))).thenReturn(mapConfig);

        ITopic<Object> topic = mock(ITopic.class);

        HazelcastInstance instance = mock(HazelcastInstance.class);
        when(instance.getConfig()).thenReturn(config);
        when(instance.getTopic(eq(CACHE_NAME))).thenReturn(topic);

        // Create a new local cache
        LocalRegionCache.builder()
                .withName(CACHE_NAME)
                .withHazelcastInstance(instance)
                .withTopic(true)
                .withEvictionConfig(evictionConfig)
                .build();

        // Obtain the message listener of the local cache
        ArgumentCaptor<MessageListener> messageListenerArgumentCaptor = ArgumentCaptor.forClass(MessageListener.class);
        verify(topic).addMessageListener(messageListenerArgumentCaptor.capture());
        MessageListener messageListener = messageListenerArgumentCaptor.getValue();

        Message message = mock(Message.class);
        Member local = mock(Member.class);
        Cluster cluster = mock(Cluster.class);
        when(cluster.getLocalMember()).thenReturn(local);
        when(message.getMessageObject()).thenReturn(new Invalidation());
        when(message.getPublishingMember()).thenReturn(local);
        when(instance.getCluster()).thenReturn(cluster);

        // Publish a message from local node
        messageListener.onMessage(message);

        // Verify that our message listener ignores messages from local node
        verify(message, never()).getMessageObject();
    }

    @Test
    public void testMessagesFromWhenInstanceIsClient() {
        MapConfig mapConfig = mock(MapConfig.class);

        LocalRegionCache.EvictionConfig evictionConfig = LocalRegionCache.EvictionConfig.create(someMapConfigWithEvictionConfig());

        Config config = mock(Config.class);
        when(config.findMapConfig(eq(CACHE_NAME))).thenReturn(mapConfig);

        ITopic<Object> topic = mock(ITopic.class);

        HazelcastInstance clientInstance = mock(HazelcastClientProxy.class);
        when(clientInstance.getConfig()).thenReturn(config);
        when(clientInstance.getTopic(eq(CACHE_NAME))).thenReturn(topic);

        // Create a new local cache
        LocalRegionCache.builder()
                .withName(CACHE_NAME)
                .withHazelcastInstance(clientInstance)
                .withTopic(true)
                .withEvictionConfig(evictionConfig)
                .build();

        // Obtain the message listener of the local cache
        ArgumentCaptor<MessageListener> messageListenerArgumentCaptor = ArgumentCaptor.forClass(MessageListener.class);
        verify(topic).addMessageListener(messageListenerArgumentCaptor.capture());
        MessageListener messageListener = messageListenerArgumentCaptor.getValue();

        Message message = mock(Message.class);
        Member local = mock(Member.class);
        Cluster cluster = mock(Cluster.class);
        when(cluster.getLocalMember()).thenReturn(local);
        when(message.getMessageObject()).thenReturn(new Invalidation());
        when(message.getPublishingMember()).thenReturn(local);
        when(clientInstance.getCluster()).thenReturn(cluster);

        messageListener.onMessage(message);

        // Verify that our message listener process messages
        verify(message).getMessageObject();
    }
}
