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

import com.hazelcast.hibernate.distributed.IMapRegionCache;
import org.hibernate.cache.cfg.spi.DomainDataRegionConfig;
import org.hibernate.cache.spi.CacheKeysFactory;
import org.hibernate.cache.spi.support.RegionNameQualifier;
import org.hibernate.engine.spi.SessionFactoryImplementor;

/**
 * Simple RegionFactory implementation to return Hazelcast based Region implementations
 */
public class HazelcastCacheRegionFactory extends AbstractHazelcastCacheRegionFactory {

    private static final PhoneHomeInfo PHONE_HOME_INFO = new PhoneHomeInfo(false);

    public HazelcastCacheRegionFactory() {
    }

    public HazelcastCacheRegionFactory(final CacheKeysFactory cacheKeysFactory) {
        super(cacheKeysFactory);
    }

    public HazelcastCacheRegionFactory(PhoneHomeService phoneHomeService) {
        super(phoneHomeService);
    }

    @Override
    protected RegionCache createRegionCache(final String unqualifiedRegionName,
                                            final SessionFactoryImplementor sessionFactory,
                                            final DomainDataRegionConfig regionConfig) {
        verifyStarted();
        assert !RegionNameQualifier.INSTANCE.isQualified(unqualifiedRegionName, sessionFactory.getSessionFactoryOptions());

        final String qualifiedRegionName = RegionNameQualifier.INSTANCE.qualify(
                unqualifiedRegionName,
                sessionFactory.getSessionFactoryOptions()
        );

        return new IMapRegionCache(this, qualifiedRegionName, instance);
    }

    @Override
    protected RegionCache createTimestampsRegionCache(final String unqualifiedRegionName,
                                                      final SessionFactoryImplementor sessionFactory) {
        verifyStarted();
        assert !RegionNameQualifier.INSTANCE.isQualified(unqualifiedRegionName, sessionFactory.getSessionFactoryOptions());

        final String qualifiedRegionName = RegionNameQualifier.INSTANCE.qualify(
                unqualifiedRegionName,
                sessionFactory.getSessionFactoryOptions()
        );

        return new IMapRegionCache(this, qualifiedRegionName, instance);
    }

    @Override
    PhoneHomeInfo phoneHomeInfo() {
        return PHONE_HOME_INFO;
    }
}
