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
import com.hazelcast.hibernate.instance.DefaultHazelcastInstanceFactory;
import com.hazelcast.hibernate.instance.IHazelcastInstanceFactory;
import com.hazelcast.hibernate.instance.IHazelcastInstanceLoader;
import com.hazelcast.hibernate.local.CleanupService;
import com.hazelcast.hibernate.local.LocalRegionCache;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.cfg.spi.DomainDataRegionBuildingContext;
import org.hibernate.cache.cfg.spi.DomainDataRegionConfig;
import org.hibernate.cache.internal.DefaultCacheKeysFactory;
import org.hibernate.cache.spi.CacheKeysFactory;
import org.hibernate.cache.spi.DomainDataRegion;
import org.hibernate.cache.spi.support.DomainDataStorageAccess;
import org.hibernate.cache.spi.support.RegionFactoryTemplate;
import org.hibernate.cache.spi.support.StorageAccess;
import org.hibernate.engine.spi.SessionFactoryImplementor;

import java.util.Map;
import java.util.Properties;

/**
 * Simple RegionFactory implementation to return Hazelcast based local Region implementations
 */
public abstract class AbstractHazelcastCacheRegionFactory extends RegionFactoryTemplate {

    protected CleanupService cleanupService;
    protected HazelcastInstance instance;

    private final CacheKeysFactory cacheKeysFactory;
    private final ILogger log = Logger.getLogger(getClass());

    private IHazelcastInstanceLoader instanceLoader;

    @SuppressWarnings("unused")
    public AbstractHazelcastCacheRegionFactory() {
        this(DefaultCacheKeysFactory.INSTANCE);
    }

    public AbstractHazelcastCacheRegionFactory(final CacheKeysFactory cacheKeysFactory) {
        this.cacheKeysFactory = cacheKeysFactory;
    }

    public AbstractHazelcastCacheRegionFactory(final HazelcastInstance instance) {
        this.instance = instance;

        cacheKeysFactory = DefaultCacheKeysFactory.INSTANCE;
    }

    @Override
    public DomainDataRegion buildDomainDataRegion(final DomainDataRegionConfig regionConfig,
                                                  final DomainDataRegionBuildingContext buildingContext) {
        return new HazelcastDomainDataRegionImpl(
                regionConfig,
                this,
                createDomainDataStorageAccess(regionConfig, buildingContext),
                cacheKeysFactory,
                buildingContext
        );
    }

    public HazelcastInstance getHazelcastInstance() {
        return instance;
    }

    @Override
    protected DomainDataStorageAccess createDomainDataStorageAccess(final DomainDataRegionConfig regionConfig,
                                                                    final DomainDataRegionBuildingContext buildingContext) {
        return new HazelcastStorageAccessImpl(
                createRegionCache(regionConfig.getRegionName(), buildingContext.getSessionFactory(), regionConfig)
        );
    }

    @Override
    protected StorageAccess createQueryResultsRegionStorageAccess(final String regionName,
                                                                  final SessionFactoryImplementor sessionFactory) {
        // Note: We don't want to use an ITopic for invalidation because the timestamps cache can take care of outdated
        // queries
        final LocalRegionCache regionCache = new LocalRegionCache(this, regionName, instance, null, false);
        cleanupService.registerCache(regionCache);
        return new HazelcastStorageAccessImpl(regionCache);
    }

    protected abstract RegionCache createRegionCache(final String unqualifiedRegionName,
                                                     final SessionFactoryImplementor sessionFactory,
                                                     final DomainDataRegionConfig regionConfig);

    @Override
    protected StorageAccess createTimestampsRegionStorageAccess(final String regionName,
                                                                final SessionFactoryImplementor sessionFactory) {
        return new HazelcastStorageAccessImpl(
                createTimestampsRegionCache(regionName, sessionFactory)
        );
    }

    protected abstract RegionCache createTimestampsRegionCache(final String regionName,
                                                               final SessionFactoryImplementor sessionFactory);

    @Override
    protected CacheKeysFactory getImplicitCacheKeysFactory() {
        return cacheKeysFactory;
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Lifecycle

    @Override
    protected boolean isStarted() {
        return super.isStarted() && instance.getLifecycleService().isRunning();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void prepareForUse(final SessionFactoryOptions settings, final Map configValues) {
        log.info("Starting up " + getClass().getSimpleName());
        if (instance == null || !instance.getLifecycleService().isRunning()) {
            final String defaultFactory = DefaultHazelcastInstanceFactory.class.getName();

            String factoryName = (String) configValues.get(CacheEnvironment.HAZELCAST_FACTORY);
            if (factoryName == null) {
                factoryName = defaultFactory;
            }

            final ClassLoader cl = Thread.currentThread().getContextClassLoader();
            try {
                final Class<IHazelcastInstanceFactory> factory =
                        (Class<IHazelcastInstanceFactory>) Class.forName(factoryName, true, cl);
                instanceLoader = factory.newInstance().createInstanceLoader(toProperties(configValues));
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                throw new CacheException("Failed to set up hazelcast instance factory", e);
            }
            instance = instanceLoader.loadInstance();
        }
        cleanupService = new CleanupService(instance.getName());
    }

    @SuppressWarnings("Duplicates")
    @Override
    protected void releaseFromUse() {
        if (instanceLoader != null) {
            log.info("Shutting down " + getClass().getSimpleName());
            instanceLoader.unloadInstance();
            instance = null;
            instanceLoader = null;
        }
        cleanupService.stop();
    }

    private Properties toProperties(final Map configValues) {
        final Properties properties = new Properties();
        properties.putAll(configValues);
        return properties;
    }
}
