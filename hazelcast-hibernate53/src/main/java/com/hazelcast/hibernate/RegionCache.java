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

    void afterUpdate(Object key, Object newValue, Object newVersion);

    @Override
    default void clear() {
        evictData();
    }

    boolean contains(Object key);

    @Override
    default void destroy() {
    }

    void evictData();

    void evictData(Object key);

    Object get(Object key, long txTimestamp);

    /**
     * Hazelcast does not support pushing elements to disk.
     *
     * @return -1 this value means "unsupported"
     */
    @Override
    default long getElementCountOnDisk() {
        return -1;
    }

    default long nextTimestamp() {
        return getRegionFactory().nextTimestamp();
    }

    boolean put(Object key, Object value, long txTimestamp, Object version);

    void unlockItem(Object key, SoftLock lock);
}
