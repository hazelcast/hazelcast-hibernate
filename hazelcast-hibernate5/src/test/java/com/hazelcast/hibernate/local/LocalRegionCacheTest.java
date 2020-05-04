package com.hazelcast.hibernate.local;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.topic.ITopic;
import com.hazelcast.topic.MessageListener;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.QuickTest;
import org.hibernate.cache.spi.CacheDataDescription;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;

import static org.mockito.Mockito.*;

@RunWith(HazelcastSerialClassRunner.class)
@Category(QuickTest.class)
@SuppressWarnings("unchecked")
public class LocalRegionCacheTest {

    private static final String CACHE_NAME = "cache";

    @Test
    public void testConstructorIgnoresUnsupportedOperationExceptionsFromConfig() {
        HazelcastInstance instance = mock(HazelcastInstance.class);
        doThrow(UnsupportedOperationException.class).when(instance).getConfig();

        new LocalRegionCache(CACHE_NAME, instance, null, false);
    }

    @Test
    public void testConstructorIgnoresVersionComparatorForUnversionedData() {
        CacheDataDescription description = mock(CacheDataDescription.class);
        doThrow(AssertionError.class).when(description).getVersionComparator(); // Will fail the test if called

        new LocalRegionCache(CACHE_NAME, null, description);
        verify(description).isVersioned(); // Verify that the versioned flag was checked
        verifyNoMoreInteractions(description);
    }

    @Test
    public void testConstructorSetsVersionComparatorForVersionedData() {
        Comparator<?> comparator = mock(Comparator.class);

        CacheDataDescription description = mock(CacheDataDescription.class);
        when(description.getVersionComparator()).thenReturn(comparator);
        when(description.isVersioned()).thenReturn(true);

        new LocalRegionCache(CACHE_NAME, null, description);
        verify(description).getVersionComparator();
        verify(description).isVersioned();
    }

    @Test
    public void testFourArgConstructorDoesNotRegisterTopicListenerIfNotRequested() {
        MapConfig mapConfig = mock(MapConfig.class);

        Config config = mock(Config.class);
        when(config.findMapConfig(eq(CACHE_NAME))).thenReturn(mapConfig);

        HazelcastInstance instance = mock(HazelcastInstance.class);
        when(instance.getConfig()).thenReturn(config);

        new LocalRegionCache(CACHE_NAME, instance, null, false);
        verify(config).findMapConfig(eq(CACHE_NAME));
        verify(instance).getConfig();
        verify(instance, never()).getTopic(anyString());
    }

    @Test
    public void testEvictionConfigIsDerivedFromMapConfig() {
        MapConfig mapConfig = mock(MapConfig.class);

        Config config = mock(Config.class);
        when(config.findMapConfig(eq(CACHE_NAME))).thenReturn(mapConfig);

        com.hazelcast.config.EvictionConfig maxSizeConfig = mock(com.hazelcast.config.EvictionConfig.class);
        when(mapConfig.getEvictionConfig()).thenReturn(maxSizeConfig);

        HazelcastInstance instance = mock(HazelcastInstance.class);
        when(instance.getConfig()).thenReturn(config);

        new LocalRegionCache(CACHE_NAME, instance, null, false)
          .cleanup();

        verify(maxSizeConfig).getSize();
        verify(mapConfig).getTimeToLiveSeconds();
    }

    @Test
    public void testEvictionConfigIsNotDerivedFromMapConfig() {
        MapConfig mapConfig = mock(MapConfig.class);

        Config config = mock(Config.class);
        when(config.findMapConfig(eq(CACHE_NAME))).thenReturn(mapConfig);

        LocalRegionCache.EvictionConfig evictionConfig = mock(LocalRegionCache.EvictionConfig.class);
        when(evictionConfig.getTimeToLive()).thenReturn(Duration.ofSeconds(1));

        new LocalRegionCache(CACHE_NAME, null, null, false, evictionConfig)
          .cleanup();

        verify(evictionConfig).getMaxSize();
        verify(evictionConfig).getTimeToLive();
        verifyZeroInteractions(mapConfig);
    }

    // Verifies that the three-argument constructor still registers a listener with a topic if the HazelcastInstance
    // is provided. This ensures the old behavior has not been regressed by adding the new four argument constructor
    @Test
    public void testThreeArgConstructorRegistersTopicListener() {
        MapConfig mapConfig = mock(MapConfig.class);

        Config config = mock(Config.class);
        when(config.findMapConfig(eq(CACHE_NAME))).thenReturn(mapConfig);

        ITopic<Object> topic = mock(ITopic.class);
        when(topic.addMessageListener(isNotNull(MessageListener.class))).thenReturn(UUID.randomUUID());

        HazelcastInstance instance = mock(HazelcastInstance.class);
        when(instance.getConfig()).thenReturn(config);
        when(instance.getTopic(eq(CACHE_NAME))).thenReturn(topic);

        new LocalRegionCache(CACHE_NAME, instance, null);
        verify(config).findMapConfig(eq(CACHE_NAME));
        verify(instance).getConfig();
        verify(instance).getTopic(eq(CACHE_NAME));
        verify(topic).addMessageListener(isNotNull(MessageListener.class));
    }

    public static void runCleanup(LocalRegionCache cache) {
        cache.cleanup();
    }
}