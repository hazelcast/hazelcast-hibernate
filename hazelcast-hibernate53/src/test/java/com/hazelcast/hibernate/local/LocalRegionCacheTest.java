package com.hazelcast.hibernate.local;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.topic.ITopic;
import com.hazelcast.topic.MessageListener;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.QuickTest;
import org.hibernate.cache.cfg.spi.CollectionDataCachingConfig;
import org.hibernate.cache.cfg.spi.DomainDataRegionConfig;
import org.hibernate.cache.cfg.spi.EntityDataCachingConfig;
import org.hibernate.cache.spi.RegionFactory;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.Collections;
import java.util.Comparator;
import java.util.function.Supplier;

import static org.mockito.Mockito.*;

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
        Supplier x = () -> comparator;
        when(entityDataCachingConfig.getVersionComparatorAccess()).thenReturn(x);

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
        MapConfig mapConfig = mock(MapConfig.class);

        Config config = mock(Config.class);
        when(config.findMapConfig(eq(CACHE_NAME))).thenReturn(mapConfig);

        HazelcastInstance instance = mock(HazelcastInstance.class);
        when(instance.getConfig()).thenReturn(config);

        new LocalRegionCache(regionFactory, CACHE_NAME, instance, null, false);
        verify(config).findMapConfig(eq(CACHE_NAME));
        verify(instance).getConfig();
        verify(instance, never()).getTopic(anyString());
    }

    // Verifies that the three-argument constructor still registers a listener with a topic if the HazelcastInstance
    // is provided. This ensures the old behavior has not been regressed by adding the new four argument constructor
    @Test
    public void testThreeArgConstructorRegistersTopicListener() {
        MapConfig mapConfig = mock(MapConfig.class);

        Config config = mock(Config.class);
        when(config.findMapConfig(eq(CACHE_NAME))).thenReturn(mapConfig);

        ITopic<Object> topic = mock(ITopic.class);
        when(topic.addMessageListener(isNotNull(MessageListener.class))).thenReturn("ignored");

        HazelcastInstance instance = mock(HazelcastInstance.class);
        when(instance.getConfig()).thenReturn(config);
        when(instance.getTopic(eq(CACHE_NAME))).thenReturn(topic);

        new LocalRegionCache(regionFactory, CACHE_NAME, instance, null, true);
        verify(config).findMapConfig(eq(CACHE_NAME));
        verify(instance).getConfig();
        verify(instance).getTopic(eq(CACHE_NAME));
        verify(topic).addMessageListener(isNotNull(MessageListener.class));
    }

    @SuppressWarnings("unused")
    public static void runCleanup(LocalRegionCache cache) {
        cache.cleanup();
    }
}