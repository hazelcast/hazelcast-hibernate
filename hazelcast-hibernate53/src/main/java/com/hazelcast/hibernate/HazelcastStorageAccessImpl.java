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

import com.hazelcast.core.OperationTimeoutException;
import com.hazelcast.logging.Logger;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.access.SoftLock;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

import java.util.Collections;
import java.util.Map;

/**
 * StorageAccess implementation wrapping a Hazelcast {@link RegionCache} reference.
 */
@SuppressWarnings("unchecked")
public class HazelcastStorageAccessImpl implements HazelcastStorageAccess {

    private final RegionCache delegate;
    private final boolean fallback;

    HazelcastStorageAccessImpl(final RegionCache delegate) {
        this(delegate, Collections.emptyMap());
    }

    public HazelcastStorageAccessImpl(RegionCache delegate, Map<String, Object> properties) {
        this.delegate = delegate;
        this.fallback = CacheEnvironment.getShouldFallback(properties);
    }

    @Override
    public void afterUpdate(final Object key, final Object newValue, final Object newVersion) {
        delegate.afterUpdate(key, newValue, newVersion);
    }

    @Override
    public boolean contains(final Object key) {
        try {
            return delegate.contains(key);
        } catch (Exception e) {
            if (fallback) {
                Logger.getLogger(HazelcastStorageAccessImpl.class).finest(e.getMessage(), e);
                return false;
            }
            throw new CacheException(e);
        }
    }

    @Override
    public void evictData() throws CacheException {
        try {
            delegate.evictData();
        } catch (Exception e) {
            if (fallback) {
                Logger.getLogger(HazelcastStorageAccessImpl.class).finest(e.getMessage(), e);
                return;
            }
            throw new CacheException(e);
        }
    }

    @Override
    public void evictData(final Object key) throws CacheException {
        try {
            delegate.evictData(key);
        } catch (Exception e) {
            if (fallback) {
                Logger.getLogger(HazelcastStorageAccessImpl.class).finest(e.getMessage(), e);
                return;
            }
            throw new CacheException(e);
        }
    }

    @Override
    public Object getFromCache(final Object key, final SharedSessionContractImplementor session) throws CacheException {
        try {
            return delegate.get(key, nextTimestamp());
        } catch (OperationTimeoutException e) {
            Logger.getLogger(HazelcastStorageAccessImpl.class).finest(e.getMessage(), e);
            return null;
        } catch (Exception e) {
            if (fallback) {
                Logger.getLogger(HazelcastStorageAccessImpl.class).finest(e.getMessage(), e);
                return null;
            }
            throw new CacheException(e);
        }

    }

    @Override
    public void putIntoCache(final Object key, final Object value, final SharedSessionContractImplementor session)
            throws CacheException {
        try {
            delegate.put(key, value, nextTimestamp(), null);
        } catch (Exception e) {
            if (fallback) {
                Logger.getLogger(HazelcastStorageAccessImpl.class).finest(e.getMessage(), e);
            } else {
                throw new CacheException(e);
            }
        }
    }

    @Override
    public void release() {
        try {
            // TODO why noop was here?
            delegate.destroy();
        } catch (Exception e) {
            if (fallback) {
                Logger.getLogger(HazelcastStorageAccessImpl.class).finest(e.getMessage(), e);
            } else {
                throw new CacheException(e);
            }
        }
    }

    @Override
    public void unlockItem(final Object key, final SoftLock lock) {
        try {
            delegate.unlockItem(key, lock);
        } catch (Exception e) {
            if (fallback) {
                Logger.getLogger(HazelcastStorageAccessImpl.class).finest(e.getMessage(), e);
            } else {
                throw new CacheException(e);
            }
        }
    }

    RegionCache getDelegate() {
        return delegate;
    }

    private long nextTimestamp() {
        return delegate.nextTimestamp();
    }
}
