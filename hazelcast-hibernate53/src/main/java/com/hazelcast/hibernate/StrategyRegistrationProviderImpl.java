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

import org.hibernate.boot.registry.selector.SimpleStrategyRegistrationImpl;
import org.hibernate.boot.registry.selector.StrategyRegistration;
import org.hibernate.boot.registry.selector.StrategyRegistrationProvider;
import org.hibernate.cache.spi.RegionFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes the Hazelcast RegionFactory available to the Hibernate
 * {@link org.hibernate.boot.registry.selector.spi.StrategySelector} service
 * under a number of keys.
 */
public class StrategyRegistrationProviderImpl implements StrategyRegistrationProvider {
    @Override
    @SuppressWarnings("unchecked")
    public Iterable<StrategyRegistration> getStrategyRegistrations() {
        final List<StrategyRegistration> strategyRegistrations = new ArrayList<>();

        strategyRegistrations.add(
                new SimpleStrategyRegistrationImpl(
                        RegionFactory.class,
                        HazelcastLocalCacheRegionFactory.class,
                        "hazelcast-local",
                        HazelcastLocalCacheRegionFactory.class.getName(),
                        HazelcastLocalCacheRegionFactory.class.getSimpleName()
                )
        );

        strategyRegistrations.add(
                new SimpleStrategyRegistrationImpl(
                        RegionFactory.class,
                        HazelcastCacheRegionFactory.class,
                        "hazelcast",
                        HazelcastCacheRegionFactory.class.getName(),
                        HazelcastCacheRegionFactory.class.getSimpleName()
                )
        );

        return strategyRegistrations;
    }
}
