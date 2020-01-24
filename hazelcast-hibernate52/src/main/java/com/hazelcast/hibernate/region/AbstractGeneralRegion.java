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

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.OperationTimeoutException;
import com.hazelcast.hibernate.RegionCache;
import com.hazelcast.logging.Logger;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.GeneralDataRegion;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

import java.util.Properties;

/**
 * Basic implementation of a IMap based cache without any special security checks
 *
 * @param <Cache> implementation type of RegionCache
 */
abstract class AbstractGeneralRegion<Cache extends RegionCache>
        extends AbstractHazelcastRegion<Cache>
        implements GeneralDataRegion {

    private final Cache cache;

    protected AbstractGeneralRegion(final HazelcastInstance instance, final String name,
                                    final Properties props, final Cache cache) {
        super(instance, name, props);
        this.cache = cache;
    }

    @Override
    public void evict(final Object key) throws CacheException {
        try {
            getCache().remove(key);
        } catch (OperationTimeoutException e) {
            Logger.getLogger(AbstractGeneralRegion.class).finest(e);
        }
    }

    @Override
    public void evictAll() throws CacheException {
        try {
            getCache().clear();
        } catch (OperationTimeoutException e) {
            Logger.getLogger(AbstractGeneralRegion.class).finest(e);
        }
    }

    @Override
    public Object get(final SharedSessionContractImplementor session, final Object key) throws CacheException {
        try {
            return getCache().get(key, nextTimestamp());
        } catch (OperationTimeoutException e) {
            return null;
        }
    }

    @Override
    public void put(final SharedSessionContractImplementor session, final Object key, final Object value) throws CacheException {
        try {
            getCache().put(key, value, nextTimestamp(), null);
        } catch (OperationTimeoutException e) {
            Logger.getLogger(AbstractGeneralRegion.class).finest(e);
        }
    }

    @Override
    public Cache getCache() {
        return cache;
    }
}
