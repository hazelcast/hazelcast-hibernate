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

package com.hazelcast.hibernate;

import org.hibernate.cache.spi.ExtendedStatisticsSupport;
import org.hibernate.cache.spi.Region;
import org.hibernate.cache.spi.access.SoftLock;

/**
 * This interface defines an internal cached region implementation.
 */
public interface RegionCache extends Region, ExtendedStatisticsSupport {

    void afterUpdate(final Object key, final Object newValue, final Object newVersion);

    @Override
    default void clear() {
        evictData();
    }

    boolean contains(final Object key);

    @Override
    default void destroy() {
    }

    void evictData();

    void evictData(final Object key);

    Object get(final Object key, final long txTimestamp);

    @Override
    default long getElementCountOnDisk() {
        return 0;
    }

    default long nextTimestamp() {
        return getRegionFactory().nextTimestamp();
    }

    boolean put(final Object key, final Object value, final long txTimestamp, final Object version);

    void unlockItem(final Object key, final SoftLock lock);
}
