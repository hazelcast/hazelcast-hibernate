/*
 * Copyright 2020 Hazelcast Inc.
 *
 * Licensed under the Hazelcast Community License (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://hazelcast.com/hazelcast-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.hazelcast.hibernate.local;

import com.hazelcast.instance.impl.OutOfMemoryErrorDispatcher;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * An internal service to clean cache regions
 */
public final class CleanupService {

    private final Duration fixedDelay;
    private final String name;
    private final ScheduledExecutorService executor;
    private final List<LocalRegionCache> localRegionCaches;

    public CleanupService(final String name, final Duration fixedDelay) {
        this.fixedDelay = fixedDelay;
        this.name = name;
        executor = Executors.newSingleThreadScheduledExecutor(new CleanupThreadFactory());
        localRegionCaches = new ArrayList<>();
    }

    public void registerCache(final LocalRegionCache cache) {
        executor.scheduleWithFixedDelay(cache::cleanup, fixedDelay.toMillis(), fixedDelay.toMillis(), TimeUnit.MILLISECONDS);
        localRegionCaches.add(cache);
    }

    public void stop() {
        executor.shutdownNow();
        localRegionCaches.forEach(LocalRegionCache::destroy);
    }

    private class CleanupThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(final Runnable r) {
            final Thread thread = new Thread(() -> {
                try {
                    r.run();
                } catch (OutOfMemoryError e) {
                    OutOfMemoryErrorDispatcher.onOutOfMemory(e);
                }
            }, name + ".hibernate.cleanup");
            thread.setDaemon(true);
            return thread;
        }
    }
}

