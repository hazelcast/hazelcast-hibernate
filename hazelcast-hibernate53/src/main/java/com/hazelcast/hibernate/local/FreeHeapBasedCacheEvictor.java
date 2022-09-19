/*
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

package com.hazelcast.hibernate.local;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Policy;
import com.hazelcast.internal.util.MemoryInfoAccessor;
import com.hazelcast.internal.util.RuntimeMemoryInfoAccessor;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class FreeHeapBasedCacheEvictor implements AutoCloseable {
    private static final int TERMINATE_TIMEOUT_SECONDS = 5;
    private final ILogger log = Logger.getLogger(getClass());

    private final ScheduledExecutorService executorService;
    private final MemoryInfoAccessor memoryInfoAccessor;
    private final Duration evictionDelay;
    private final AtomicBoolean started = new AtomicBoolean();

    FreeHeapBasedCacheEvictor() {
        this(Executors.newSingleThreadScheduledExecutor(defaultThreadFactory()), new RuntimeMemoryInfoAccessor(), Duration.ofSeconds(1));
    }

    /**
     * just for testing
     */
    FreeHeapBasedCacheEvictor(ScheduledExecutorService executorService, MemoryInfoAccessor memoryInfoAccessor, Duration evictionDelay) {
        this.executorService = executorService;
        this.memoryInfoAccessor = memoryInfoAccessor;
        this.evictionDelay = evictionDelay;
    }

    void start(Cache<?, ?> cache, long minimalHeapSizeInMB) {
        if (started.compareAndSet(false, true)) {
            startEvictionInBackground(cache, minimalHeapSizeInMB);
        } else {
            throw new IllegalStateException(FreeHeapBasedCacheEvictor.class.getSimpleName() + " already started");
        }
    }

    private void startEvictionInBackground(Cache<?, ?> cache, long minimalHeapSizeInMB) {
        Policy.Eviction<?, ?> eviction = cache.policy().eviction()
                .orElseThrow(() -> new IllegalStateException("Eviction not enabled"));
        log.info("Starting free-heap-size-based eviction");
        executorService.scheduleWithFixedDelay(() -> {
            if (freeHeapTooSmall(minimalHeapSizeInMB)) {
                eviction.coldest(15).forEach((key, value) -> {
                    cache.invalidate(key);
                });
            }
        }, 0, evictionDelay.toMillis(), TimeUnit.MILLISECONDS);
    }

    static ThreadFactory defaultThreadFactory() {
        return r -> new Thread(r, FreeHeapBasedCacheEvictor.class.getSimpleName() + "-free-heap-evictor");
    }

    private boolean freeHeapTooSmall(long minimalHeapSizeInMB) {
        return availableMemoryInBytes() < minimalHeapSizeInMB;
    }

    private long availableMemoryInBytes() {
        return memoryInfoAccessor.getFreeMemory() + memoryInfoAccessor.getMaxMemory() - memoryInfoAccessor.getTotalMemory();
    }

    @Override
    public void close() {
        log.info("Shutting down " + FreeHeapBasedCacheEvictor.class.getSimpleName());
        executorService.shutdown();
        try {
            boolean success = executorService.awaitTermination(TERMINATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!success) {
                log.warning("ExecutorService awaitTermination could not completed gracefully in "
                        + TERMINATE_TIMEOUT_SECONDS + " seconds. Terminating forcefully.");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warning("ExecutorService awaitTermination is interrupted. Terminating forcefully.", e);
            executorService.shutdownNow();
        }
    }
}
