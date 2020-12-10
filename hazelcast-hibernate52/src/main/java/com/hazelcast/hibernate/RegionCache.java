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

import org.hibernate.cache.spi.access.SoftLock;

import java.util.Map;

/**
 * This interface defines an internal cached region implementation as well as a mechanism
 * to unmap the cache to an underlying Map data-structure
 */
public interface RegionCache {

    Object get(Object key, long txTimestamp);

    boolean insert(Object key, Object value, Object currentVersion);

    boolean put(Object key, Object value, long txTimestamp, Object version);

    boolean update(Object key, Object newValue, Object newVersion, SoftLock lock);

    boolean remove(Object key);

    SoftLock tryLock(Object key, Object version);

    void unlock(Object key, SoftLock lock);

    boolean contains(Object key);

    void clear();

    default void destroy() {
    }

    long size();

    long getSizeInMemory();

    Map asMap();
}
