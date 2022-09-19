package com.hazelcast.hibernate.local;/*
 * Copyright (c) 2008-2022, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hazelcast.map.impl.eviction.ZeroMemoryInfoAccessor;
import org.junit.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FreeHeapBasedCacheEvictorTest {

    private static final Duration TEST_EVICTION_DELAY = Duration.ofMillis(50);

    @Test
    public void should_start_async_job() {
        ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
        FreeHeapBasedCacheEvictor sut = new FreeHeapBasedCacheEvictor(executorService, new ZeroMemoryInfoAccessor(), TEST_EVICTION_DELAY);
        Cache<?, ?> cache = Caffeine.newBuilder()
                //enable eviction operations
                .maximumSize(Long.MAX_VALUE)
                .build();

        sut.start(cache, 123);

        verify(executorService).scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());
    }

    @Test
    public void should_shutdown_executorService() throws InterruptedException {
        ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
        when(executorService.awaitTermination(anyLong(), any())).thenReturn(true);
        FreeHeapBasedCacheEvictor sut = new FreeHeapBasedCacheEvictor(executorService, new ZeroMemoryInfoAccessor(), TEST_EVICTION_DELAY);
        Cache<?, ?> cache = Caffeine.newBuilder()
                //enable eviction operations
                .maximumSize(Long.MAX_VALUE)
                .build();

        sut.close();

        verify(executorService).shutdown();
        verify(executorService).awaitTermination(anyLong(), any());
    }

    @Test
    public void should_forbid_second_start() {
        ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
        FreeHeapBasedCacheEvictor sut = new FreeHeapBasedCacheEvictor(executorService, new ZeroMemoryInfoAccessor(), TEST_EVICTION_DELAY);
        Cache<?, ?> cache = Caffeine.newBuilder()
                //enable eviction operations
                .maximumSize(Long.MAX_VALUE)
                .build();

        sut.start(cache, 123);

        assertThatThrownBy(() -> sut.start(cache, 123))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("FreeHeapBasedCacheEvictor already started");
    }

    @Test
    public void should_evict_entries() {
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(FreeHeapBasedCacheEvictor.defaultThreadFactory());

        FreeHeapBasedCacheEvictor sut = new FreeHeapBasedCacheEvictor(executorService, new ZeroMemoryInfoAccessor(), TEST_EVICTION_DELAY);
        Cache<Integer, Integer> cache = Caffeine.newBuilder()
                //enable eviction operations
                .maximumSize(Long.MAX_VALUE)
                .build();
        IntStream.range(0, 25).forEach(i -> cache.put(i, i));

        assertThat(cache.estimatedSize()).isEqualTo(25);

        sut.start(cache, 100);

        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> cache.estimatedSize() == 0);
        assertThat(allThreads()).anyMatch(thread -> thread.getName().contains("-free-heap-evictor"));

        sut.close();

        assertThat(allThreads()).noneMatch(thread -> thread.getName().contains("-free-heap-evictor"));
    }

    private static Set<Thread> allThreads() {
        return Thread.getAllStackTraces().keySet();
    }

    @Test
    public void should_not_evict_entries() {
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

        FreeHeapBasedCacheEvictor sut = new FreeHeapBasedCacheEvictor(executorService, new ZeroMemoryInfoAccessor(), TEST_EVICTION_DELAY);
        Cache<Integer, Integer> cache = Caffeine.newBuilder()
                //enable eviction operations
                .maximumSize(Long.MAX_VALUE)
                .build();
        IntStream.range(0, 50).forEach(i -> cache.put(i, i));

        assertThat(cache.estimatedSize()).isEqualTo(50);

        sut.start(cache, 0);

        await().pollDelay(1, TimeUnit.SECONDS)
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> cache.estimatedSize() == 50);
    }

}
