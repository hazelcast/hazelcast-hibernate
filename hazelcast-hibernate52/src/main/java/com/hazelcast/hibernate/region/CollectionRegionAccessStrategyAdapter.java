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

package com.hazelcast.hibernate.region;

import com.hazelcast.hibernate.access.AccessDelegate;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.CacheKeysFactory;
import org.hibernate.cache.spi.CollectionRegion;
import org.hibernate.cache.spi.access.CollectionRegionAccessStrategy;
import org.hibernate.cache.spi.access.SoftLock;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.collection.CollectionPersister;

/**
 * Simple adapter implementation for transactional / concurrent access control on collections
 */
public final class CollectionRegionAccessStrategyAdapter implements CollectionRegionAccessStrategy {

    private final AccessDelegate<? extends HazelcastCollectionRegion> delegate;

    private final CacheKeysFactory cacheKeysFactory = new HazelcastCacheKeysFactory();

    public CollectionRegionAccessStrategyAdapter(final AccessDelegate<? extends HazelcastCollectionRegion> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void evict(final Object key) throws CacheException {
        delegate.evict(key);
    }

    @Override
    public void evictAll() throws CacheException {
        delegate.evictAll();
    }

    @Override
    public Object generateCacheKey(final Object id, final CollectionPersister persister,
                                   final SessionFactoryImplementor session, final String tenantIdentifier) {
        return cacheKeysFactory.createCollectionKey(id, persister, session, tenantIdentifier);
    }

    @Override
    public Object get(final SharedSessionContractImplementor session, final Object key, final long txTimestamp)
            throws CacheException {
        return delegate.get(key, txTimestamp);
    }

    @Override
    public Object getCacheKeyId(final Object cacheKey) {
        return cacheKeysFactory.getCollectionId(cacheKey);
    }

    @Override
    public CollectionRegion getRegion() {
        return delegate.getHazelcastRegion();
    }

    @Override
    public SoftLock lockItem(final SharedSessionContractImplementor session, final Object key, final Object version)
            throws CacheException {
        return delegate.lockItem(key, version);
    }

    @Override
    public SoftLock lockRegion() throws CacheException {
        return delegate.lockRegion();
    }

    @Override
    public boolean putFromLoad(final SharedSessionContractImplementor session, final Object key, final Object value,
                               final long txTimestamp, final Object version) throws CacheException {
        return delegate.putFromLoad(key, value, txTimestamp, version);
    }

    @Override
    public boolean putFromLoad(final SharedSessionContractImplementor session, final Object key, final Object value,
                               final long txTimestamp, final Object version, final boolean minimalPutOverride)
            throws CacheException {
        return delegate.putFromLoad(key, value, txTimestamp, version, minimalPutOverride);
    }

    @Override
    public void remove(final SharedSessionContractImplementor session, final Object key) throws CacheException {
        delegate.remove(key);
    }

    @Override
    public void removeAll() throws CacheException {
        delegate.removeAll();
    }

    @Override
    public void unlockItem(final SharedSessionContractImplementor session, final Object key, final SoftLock lock)
            throws CacheException {
        delegate.unlockItem(key, lock);
    }

    @Override
    public void unlockRegion(final SoftLock lock) throws CacheException {
        delegate.unlockRegion(lock);
    }
}
