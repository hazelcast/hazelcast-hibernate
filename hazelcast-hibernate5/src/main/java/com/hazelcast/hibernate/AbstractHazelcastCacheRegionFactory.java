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
import com.hazelcast.hibernate.region.HazelcastQueryResultsRegion;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.QueryResultsRegion;
import org.hibernate.cache.spi.RegionFactory;
import org.hibernate.cache.spi.access.AccessType;

import java.util.Properties;

import static com.hazelcast.hibernate.CacheEnvironment.getCacheCleanup;
import static java.lang.Class.forName;

/**
 * Abstract superclass of Hazelcast based {@link RegionFactory} implementations
 */
public abstract class AbstractHazelcastCacheRegionFactory implements RegionFactory {

    protected HazelcastInstance instance;
    protected CleanupService cleanupService;
    private final PhoneHomeService phoneHomeService;
    private final ILogger log = Logger.getLogger(getClass());

    private IHazelcastInstanceLoader instanceLoader;

    public AbstractHazelcastCacheRegionFactory() {
         phoneHomeService = new PhoneHomeService(phoneHomeInfo());
    }

    public AbstractHazelcastCacheRegionFactory(final Properties properties) {
        this();
    }

    public AbstractHazelcastCacheRegionFactory(PhoneHomeService phoneHomeService) {
        this.phoneHomeService = phoneHomeService;
    }

    @Override
    public final QueryResultsRegion buildQueryResultsRegion(final String regionName, final Properties properties)
            throws CacheException {
        HazelcastQueryResultsRegion region = new HazelcastQueryResultsRegion(instance, regionName, properties);
        cleanupService.registerCache(region.getCache());
        return region;
    }

    /**
     * @return true - for a large cluster, unnecessary puts will most likely slow things down.
     */
    @Override
    public boolean isMinimalPutsEnabledByDefault() {
        return true;
    }

    @Override
    public long nextTimestamp() {
        return HazelcastTimestamper.nextTimestamp(instance);
    }

    @Override
    public void start(final SessionFactoryOptions options, final Properties properties) throws CacheException {
        log.info("Starting up " + getClass().getSimpleName());
        if (instance == null || !instance.getLifecycleService().isRunning()) {
            instanceLoader = resolveInstanceLoader(properties);
            instance = instanceLoader.loadInstance();
        }

        cleanupService = new CleanupService(instance.getName(), getCacheCleanup(properties));
        phoneHomeService.start();
    }

    private IHazelcastInstanceLoader resolveInstanceLoader(Properties properties) {
        String factoryName = properties.getProperty(CacheEnvironment.HAZELCAST_FACTORY);
        if (factoryName != null) {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            try {
                Class<IHazelcastInstanceFactory> factory = (Class<IHazelcastInstanceFactory>) forName(factoryName, true, cl);
                return factory.newInstance().createInstanceLoader(properties);
            } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
                throw new CacheException("Failed to set up hazelcast instance factory", e);
            }
        } else {
            return new DefaultHazelcastInstanceFactory().createInstanceLoader(properties);
        }
    }

    @Override
    public void stop() {
        cleanupService.stop();
        phoneHomeService.shutdown();
        if (instanceLoader != null) {
            log.info("Shutting down " + getClass().getSimpleName());
            instanceLoader.unloadInstance();
            instance = null;
            instanceLoader = null;
        }
    }

    public HazelcastInstance getHazelcastInstance() {
        return instance;
    }

    @Override
    public AccessType getDefaultAccessType() {
        return AccessType.READ_WRITE;
    }

    /**
     * @return PhoneHomeInfo to be sent to the call home server based on region type.
     */
    abstract PhoneHomeInfo phoneHomeInfo();

}
