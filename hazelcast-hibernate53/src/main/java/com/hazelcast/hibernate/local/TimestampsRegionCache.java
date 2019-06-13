/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
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

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.topic.ITopic;
import com.hazelcast.topic.MessageListener;
import com.hazelcast.hibernate.RegionCache;
import com.hazelcast.hibernate.serialization.Expirable;
import com.hazelcast.hibernate.serialization.Value;
import org.hibernate.cache.spi.RegionFactory;

/**
 * A timestamp based local RegionCache
 */
public class TimestampsRegionCache extends LocalRegionCache implements RegionCache {

    /**
     * @param regionFactory     the region factory
     * @param name              the name for this region cache, which is also used to retrieve configuration/topic
     * @param hazelcastInstance the {@code HazelcastInstance} to which this region cache belongs, used to retrieve
     *                          configuration and to lookup an {@link ITopic} to register a {@link MessageListener}
     *                          with (optional)
     */
    public TimestampsRegionCache(final RegionFactory regionFactory, final String name,
                                 final HazelcastInstance hazelcastInstance) {
        super(regionFactory, name, hazelcastInstance, null);
    }

    @Override
    public void evictData() {
        cache.clear();
        maybeNotifyTopic(null, -1L, null);
    }

    @Override
    public boolean put(final Object key, final Object value, final long txTimestamp, final Object version) {
        // use the value in txTimestamp as the timestamp instead of the value, since
        // hibernate pre-invalidates with a large value, and then invalidates with
        //the actual time, which can cause queries to not be cached.
        boolean succeed = super.put(key, value, txTimestamp, version);
        if (succeed) {
            maybeNotifyTopic(key, value, version);
        }
        return succeed;
    }

    @Override
    protected Object createMessage(final Object key, final Object value, final Object currentVersion) {
        return new Timestamp(key, (Long) value);
    }

    @SuppressWarnings("Duplicates")
    @Override
    protected void maybeInvalidate(final Object messageObject) {
        final Timestamp ts = (Timestamp) messageObject;
        final Object key = ts.getKey();

        if (key == null) {
            // Invalidate the entire region cache.
            cache.clear();
            return;
        }

        for (; ; ) {
            final Expirable value = cache.get(key);
            final Long current = value != null ? (Long) value.getValue() : null;
            if (current != null) {
                if (ts.getTimestamp() > current) {
                    if (cache.replace(key, value, new Value(value.getVersion(), nextTimestamp(), ts.getTimestamp()))) {
                        return;
                    }
                } else {
                    return;
                }
            } else {
                if (cache.putIfAbsent(key, new Value(null, nextTimestamp(), ts.getTimestamp())) == null) {
                    return;
                }
            }
        }
    }

    @Override
    final void cleanup() {
    }
}
