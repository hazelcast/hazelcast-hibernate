package com.hazelcast.hibernate.local;

import com.hazelcast.cluster.Cluster;
import com.hazelcast.cluster.Member;
import com.hazelcast.config.Config;
import com.hazelcast.config.EvictionConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MaxSizePolicy;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.hibernate.CacheEnvironment;
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

import java.util.Collections;
import java.util.Comparator;
import java.util.UUID;
import java.util.function.Supplier;

import static com.hazelcast.config.EvictionConfig.DEFAULT_MAX_SIZE_POLICY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
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
    public void testConstructorIgnoresUnsupportedOperationExceptionsFromConfig() {
        HazelcastInstance instance = mock(HazelcastInstance.class);
        doThrow(UnsupportedOperationException.class).when(instance).getConfig();

        new LocalRegionCache(regionFactory, CACHE_NAME, instance, null, false);
    }

    @Test
    public void testConstructorIgnoresVersionComparatorForUnversionedEntityData() {
        DomainDataRegionConfig domainDataRegionConfig = mock(DomainDataRegionConfig.class);
        EntityDataCachingConfig entityDataCachingConfig = mock(EntityDataCachingConfig.class);
        when(domainDataRegionConfig.getEntityCaching()).thenReturn(Collections.singletonList(entityDataCachingConfig));
        doThrow(AssertionError.class).when(entityDataCachingConfig).getVersionComparatorAccess(); // Will fail the test if called

        new LocalRegionCache(regionFactory, CACHE_NAME, null, domainDataRegionConfig, false);
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

        new LocalRegionCache(regionFactory, CACHE_NAME, null, domainDataRegionConfig, false);
        verify(entityDataCachingConfig).isVersioned();
        verify(entityDataCachingConfig).getVersionComparatorAccess();
    }

    @Test
    public void testConstructorIgnoresVersionComparatorForUnversionedCollectionData() {
        DomainDataRegionConfig domainDataRegionConfig = mock(DomainDataRegionConfig.class);
        CollectionDataCachingConfig collectionDataCachingConfig = mock(CollectionDataCachingConfig.class);
        when(domainDataRegionConfig.getCollectionCaching()).thenReturn(Collections.singletonList(collectionDataCachingConfig));
        doThrow(AssertionError.class).when(collectionDataCachingConfig).getOwnerVersionComparator(); // Will fail the test if called

        new LocalRegionCache(regionFactory, CACHE_NAME, null, domainDataRegionConfig, false);
        verify(collectionDataCachingConfig).isVersioned(); // Verify that the versioned flag was checked
        verifyNoMoreInteractions(collectionDataCachingConfig);
    }

    @Test
    public void testConstructorSetsVersionComparatorForVersionedCollectionData() {
        Comparator<?> comparator = mock(Comparator.class);

        DomainDataRegionConfig domainDataRegionConfig = mock(DomainDataRegionConfig.class);
        CollectionDataCachingConfig collectionDataCachingConfig = mock(CollectionDataCachingConfig.class);
        when(domainDataRegionConfig.getCollectionCaching()).thenReturn(Collections.singletonList(collectionDataCachingConfig));
        when(collectionDataCachingConfig.isVersioned()).thenReturn(true);
        when(collectionDataCachingConfig.getOwnerVersionComparator()).thenReturn(comparator);

        new LocalRegionCache(regionFactory, CACHE_NAME, null, domainDataRegionConfig, false);
        verify(collectionDataCachingConfig).getOwnerVersionComparator();
        verify(collectionDataCachingConfig).isVersioned();
    }

    @Test
    public void testFourArgConstructorDoesNotRegisterTopicListenerIfNotRequested() {
        MapConfig mapConfig = someMapConfigWithEvictionConfig();

        Config config = mock(Config.class);
        when(config.findMapConfig(eq(CACHE_NAME))).thenReturn(mapConfig);

        HazelcastInstance instance = mock(HazelcastInstance.class);
        when(instance.getConfig()).thenReturn(config);

        new LocalRegionCache(regionFactory, CACHE_NAME, instance, null, false);
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

        new LocalRegionCache(regionFactory, CACHE_NAME, instance, null, false);

        verify(maxSizeConfig, atLeastOnce()).getSize();
        verify(maxSizeConfig, atLeastOnce()).getMaxSizePolicy();
        verify(mapConfig, atLeastOnce()).getTimeToLiveSeconds();
    }

    @Test
    public void test_EvictionConfig_creation_from_MapConfig() {
        MapConfig mapConfig = someMapConfigWithEvictionConfig();

        LocalRegionCache.EvictionConfig evictionConfig = LocalRegionCache.EvictionConfig.create(mapConfig);

        assertThat(evictionConfig.getTimeToLive()).hasSeconds(123);
        assertThat(evictionConfig.getSize()).isEqualTo(234);
        assertThat(evictionConfig.getMaxSizePolicy()).isEqualTo(MaxSizePolicy.ENTRY_COUNT);
    }

    private MapConfig someMapConfigWithEvictionConfig() {
        return spy(new MapConfig()
                .setTimeToLiveSeconds(123)
                .setEvictionConfig(spy(new EvictionConfig()
                        .setSize(234)
                        .setMaxSizePolicy(MaxSizePolicy.ENTRY_COUNT))
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

        new LocalRegionCache(regionFactory, CACHE_NAME, null, null, false, evictionConfig);

        verify(evictionConfig, atLeastOnce()).getSize();
        verify(evictionConfig, atLeastOnce()).getTimeToLive();
        verify(evictionConfig, atLeastOnce()).getMaxSizePolicy();
    }

    // Verifies that the three-argument constructor still registers a listener with a topic if the HazelcastInstance
    // is provided. This ensures the old behavior has not been regressed by adding the new four argument constructor
    @Test
    public void testThreeArgConstructorRegistersTopicListener() {
        MapConfig mapConfig = someMapConfigWithEvictionConfig();

        Config config = mock(Config.class);
        when(config.findMapConfig(eq(CACHE_NAME))).thenReturn(mapConfig);

        ITopic<Object> topic = mock(ITopic.class);
        when(topic.addMessageListener(isNotNull())).thenReturn(UUID.randomUUID());

        HazelcastInstance instance = mock(HazelcastInstance.class);
        when(instance.getConfig()).thenReturn(config);
        when(instance.getTopic(eq(CACHE_NAME))).thenReturn(topic);

        new LocalRegionCache(regionFactory, CACHE_NAME, instance, null, true);
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
        new LocalRegionCache(null, CACHE_NAME, instance, null, true, evictionConfig);

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
}
