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
import org.hibernate.cache.spi.NaturalIdRegion;
import org.hibernate.cache.spi.access.NaturalIdRegionAccessStrategy;
import org.hibernate.cache.spi.access.SoftLock;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.entity.EntityPersister;

/**
 * Simple adapter implementation for transactional / concurrent access control on natural ids
 */
public final class NaturalIdRegionAccessStrategyAdapter implements NaturalIdRegionAccessStrategy {

    private final AccessDelegate<? extends HazelcastNaturalIdRegion> delegate;

    private final CacheKeysFactory cacheKeysFactory = new HazelcastCacheKeysFactory();

    public NaturalIdRegionAccessStrategyAdapter(final AccessDelegate<? extends HazelcastNaturalIdRegion> delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean afterInsert(final SharedSessionContractImplementor session, final Object key, final Object value)
            throws CacheException {
        return delegate.afterInsert(key, value, null);
    }

    @Override
    public boolean afterUpdate(final SharedSessionContractImplementor session, final Object key, final Object value,
                               final SoftLock lock) throws CacheException {
        return delegate.afterUpdate(key, value, null, null, lock);
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
    public Object generateCacheKey(final Object[] naturalIdValues, final EntityPersister persister,
                                   final SharedSessionContractImplementor session) {
        return cacheKeysFactory.createNaturalIdKey(naturalIdValues, persister, session);
    }

    @Override
    public Object get(final SharedSessionContractImplementor session, final Object key, final long txTimestamp)
            throws CacheException {
        return delegate.get(key, txTimestamp);
    }

    @Override
    public Object[] getNaturalIdValues(final Object cacheKey) {
        return cacheKeysFactory.getNaturalIdValues(cacheKey);
    }

    @Override
    public NaturalIdRegion getRegion() {
        return delegate.getHazelcastRegion();
    }

    @Override
    public boolean insert(final SharedSessionContractImplementor session, final Object key, final Object value)
            throws CacheException {
        return delegate.insert(key, value, null);
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

    @Override
    public boolean update(final SharedSessionContractImplementor session, final Object key, final Object value)
            throws CacheException {
        return delegate.update(key, value, null, null);
    }
}
