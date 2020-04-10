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
import org.hibernate.cache.CacheException;

import java.util.Properties;

/**
 * Factory interface to build Hazelcast instances and configure them depending
 * on configuration.
 */
public interface IHazelcastInstanceLoader {

    /**
     * Applies a set of properties to the factory
     *
     * @param props properties to apply
     */
    void configure(final Properties props);

    /**
     * Create a new {@link HazelcastInstance} or loads an already
     * existing instances by it's name.
     *
     * @return new or existing HazelcastInstance (either client or node mode)
     * @throws CacheException all exceptions wrapped to CacheException
     */
    HazelcastInstance loadInstance() throws CacheException;

    /**
     * Tries to shutdown a HazelcastInstance
     *
     * @throws CacheException all exceptions wrapped to CacheException
     */
    void unloadInstance() throws CacheException;
}

