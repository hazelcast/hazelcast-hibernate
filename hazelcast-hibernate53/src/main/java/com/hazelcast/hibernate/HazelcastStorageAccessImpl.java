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

import com.hazelcast.logging.Logger;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.access.SoftLock;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A custom {@link org.hibernate.cache.spi.support.DomainDataStorageAccess} implementation delegating
 * to one of Hazelcast {@link RegionCache} implementations.
 */
public class HazelcastStorageAccessImpl implements HazelcastStorageAccess {

    private final RegionCache delegate;
    private final boolean fallback;

    HazelcastStorageAccessImpl(final RegionCache delegate, boolean fallback) {
        this.delegate = delegate;
        this.fallback = fallback;
    }

    @Override
    public void afterUpdate(final Object key, final Object newValue, final Object newVersion) {
        tryWithFallback(cache -> cache.afterUpdate(key, newValue, newVersion));
    }

    @Override
    public boolean contains(final Object key) {
        return tryWithFallback(cache -> cache.contains(key), false);
    }

    @Override
    public void evictData() throws CacheException {
        tryWithFallback(RegionCache::evictData);
    }

    @Override
    public void evictData(final Object key) throws CacheException {
        tryWithFallback(cache -> cache.evictData(key));
    }

    @Override
    public Object getFromCache(final Object key, final SharedSessionContractImplementor session) throws CacheException {
        return tryWithFallback(cache -> cache.get(key, delegate.nextTimestamp()), null);
    }

    @Override
    public void putIntoCache(final Object key, final Object value, final SharedSessionContractImplementor session)
      throws CacheException {
        tryWithFallback(cache -> cache.put(key, value, delegate.nextTimestamp(), null));
    }

    @Override
    public void release() {
        delegate.destroy();
    }

    @Override
    public void unlockItem(final Object key, final SoftLock lock) {
        tryWithFallback(cache -> cache.unlockItem(key, lock));
    }

    RegionCache getDelegate() {
        return delegate;
    }

    private void tryWithFallback(Consumer<RegionCache> action) {
        tryWithFallback(cache -> {
            action.accept(cache);
            return null;
        }, null);
    }

    private <T> T tryWithFallback(Function<RegionCache, T> action, T fallbackValue) {
        try {
            return action.apply(delegate);
        } catch (Exception e) {
            if (fallback) {
                Logger.getLogger(HazelcastStorageAccessImpl.class).finest(e.getMessage(), e);
                return fallbackValue;
            }
            if (e instanceof CacheException) {
                throw e;
            } else {
                throw new CacheException(e);
            }
        }
    }
}
