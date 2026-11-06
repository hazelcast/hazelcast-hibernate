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

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.hibernate.local.LocalRegionCache;
import com.hazelcast.hibernate.local.TimestampsRegionCache;
import com.hazelcast.internal.util.Clock;
import org.hibernate.cache.cfg.spi.DomainDataRegionConfig;
import org.hibernate.cache.spi.CacheKeysFactory;
import org.hibernate.cache.spi.support.RegionNameQualifier;
import org.hibernate.engine.spi.SessionFactoryImplementor;

/**
 * Simple RegionFactory implementation to return Hazelcast based local Region implementations
 */
public class HazelcastLocalCacheRegionFactory extends AbstractHazelcastCacheRegionFactory {

    private static final PhoneHomeInfo PHONE_HOME_INFO = new PhoneHomeInfo(true);

    public HazelcastLocalCacheRegionFactory() {
    }

    public HazelcastLocalCacheRegionFactory(final CacheKeysFactory cacheKeysFactory) {
        super(cacheKeysFactory);
    }

    public HazelcastLocalCacheRegionFactory(PhoneHomeService phoneHomeService) {
        super(phoneHomeService);
    }

    public HazelcastLocalCacheRegionFactory(HazelcastInstance instance) {
        super(instance);
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
        final LocalRegionCache regionCache = LocalRegionCache.builder().withRegionFactory(this)
                .withName(qualifiedRegionName)
                .withHazelcastInstance(instance)
                .withRegionConfig(regionConfig)
                .withTopic(true)
                .withFreeHeapBasedCacheEvictor(freeHeapBasedCacheEvictor)
                .build();
        localRegionCaches.add(regionCache);
        return regionCache;
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

        TimestampsRegionCache timestampsRegionCache = new TimestampsRegionCache(this, qualifiedRegionName, instance,
                freeHeapBasedCacheEvictor);
        localRegionCaches.add(timestampsRegionCache);
        return timestampsRegionCache;
    }

    @Override
    PhoneHomeInfo phoneHomeInfo() {
        return PHONE_HOME_INFO;
    }

    public long nextTimestamp() {
        return instance == null
          ? Clock.currentTimeMillis()
          : HazelcastTimestamper.nextTimestamp(instance);
    }
}
