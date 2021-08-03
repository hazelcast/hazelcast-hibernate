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

package com.hazelcast.hibernate.serialization;

import com.hazelcast.logging.Logger;
import com.hazelcast.nio.serialization.Serializer;
import com.hazelcast.nio.serialization.SerializerHook;

/**
 * This class is used to register a special serializer to not lose
 * power over serialization in Hibernate 5.3.
 */
public class Hibernate5CacheEntrySerializerHook implements SerializerHook {

    private static final String SKIP_INIT_MSG = "Hibernate 5 not available, skipping serializer initialization";

    private final Class<?> cacheEntryClass = tryLoadCacheEntryImpl();

    private static Class<?> tryLoadCacheEntryImpl() {
        try {
            // First check if Hibernate is present at all.
            // We have to do it, because when Hibernate CacheEntry
            // is not available then loading Hazelcast CacheEntryImpl
            // throws NoClassDefFoundError. This makes some application
            // containers (Wildfly) to freak out and log scary warnings.

            // When Hibernate is NOT available then it will throw just ClassNotFoundException
            // which we catch anyway. The important difference is that
            // ClassNotFoundException is seen as less severe than NoClassDefFoundError
            // and JBoss/Wildfly won't bother logging it on its own.
            Class.forName("org.hibernate.cache.spi.entry.CacheEntry");

            // If we are here then Hibernate is available and we can safely
            // load CacheEntryImpl
            return CacheEntryImpl.class;
        } catch (Throwable e) {
            Logger.getLogger(Hibernate5CacheEntrySerializerHook.class).finest(SKIP_INIT_MSG);
            return null;
        }
    }

    @Override
    public Serializer createSerializer() {
        return cacheEntryClass == null ? null : new Hibernate53CacheEntrySerializer();
    }

    @Override
    public Class<?> getSerializationType() {
        return cacheEntryClass;
    }

    @Override
    public boolean isOverwritable() {
        return true;
    }
}
