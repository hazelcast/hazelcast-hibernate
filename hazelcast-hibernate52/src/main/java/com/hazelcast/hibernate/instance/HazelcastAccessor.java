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

package com.hazelcast.hibernate.instance;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.hibernate.AbstractHazelcastCacheRegionFactory;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cache.spi.RegionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;

/**
 * Access underlying HazelcastInstance using Hibernate SessionFactory
 *
 * @deprecated Set instanceName for your Hazelcast instance and use
 *             {@link com.hazelcast.core.Hazelcast#getHazelcastInstanceByName(String instanceName)} instead
 */
@Deprecated
@SuppressWarnings("deprecation")
public final class HazelcastAccessor {

    static final ILogger LOGGER = Logger.getLogger(HazelcastAccessor.class);

    private HazelcastAccessor() {
    }

    /**
     * Tries to extract <code>HazelcastInstance</code> from <code>Session</code>.
     *
     * @param session the {@code Session} to retrieve the Hazelcast instance for
     * @return Currently used <code>HazelcastInstance</code> or null if an error occurs.
     */
    public static HazelcastInstance getHazelcastInstance(final Session session) {
        return getHazelcastInstance(session.getSessionFactory());
    }

    /**
     * Tries to extract <code>HazelcastInstance</code> from <code>SessionFactory</code>.
     *
     * @param sessionFactory the {@code SessionFactory} to retrieve the Hazelcast instance for
     * @return Currently used <code>HazelcastInstance</code> or null if an error occurs.
     */
    public static HazelcastInstance getHazelcastInstance(final SessionFactory sessionFactory) {
        if (!(sessionFactory instanceof SessionFactoryImplementor)) {
            LOGGER.warning("SessionFactory is expected to be instance of SessionFactoryImplementor.");
            return null;
        }
        return getHazelcastInstance((SessionFactoryImplementor) sessionFactory);
    }

    /**
     * Tries to extract <code>HazelcastInstance</code> from <code>SessionFactoryImplementor</code>.
     *
     * @param sessionFactory the {@code SessionFactoryImplementor} to retrieve the Hazelcast instance for
     * @return currently used <code>HazelcastInstance</code> or null if an error occurs.
     */
    public static HazelcastInstance getHazelcastInstance(final SessionFactoryImplementor sessionFactory) {
        final RegionFactory rf = sessionFactory.getSessionFactoryOptions()
                .getServiceRegistry().getService(RegionFactory.class);
        if (rf instanceof AbstractHazelcastCacheRegionFactory) {
            return ((AbstractHazelcastCacheRegionFactory) rf).getHazelcastInstance();
        } else {
            LOGGER.warning("Current 2nd level cache implementation is not HazelcastCacheRegionFactory!");
        }
        return null;
    }
}
