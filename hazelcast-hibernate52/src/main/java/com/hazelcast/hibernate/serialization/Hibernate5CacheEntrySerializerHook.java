/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.hibernate.serialization;

import com.hazelcast.logging.Logger;
import com.hazelcast.nio.serialization.Serializer;
import com.hazelcast.nio.serialization.SerializerHook;

/**
 * This class is used to register a special serializer to not loose
 * power over serialization in Hibernate 5.
 */
public class Hibernate5CacheEntrySerializerHook implements SerializerHook {

    private static final String SKIP_INIT_MSG = "Hibernate 5 not available, skipping serializer initialization";

    private final Class<?> cacheEntryClass;

    public Hibernate5CacheEntrySerializerHook() {
        Class<?> cacheEntryClass = null;
        try {
            // check if Hibernate is available
            Class.forName("org.hibernate.cache.spi.entry.CacheEntry");
            cacheEntryClass = CacheEntryImpl.class;
        } catch (Exception e) {
            Logger.getLogger(Hibernate5CacheEntrySerializerHook.class).finest(SKIP_INIT_MSG);
        }
        this.cacheEntryClass = cacheEntryClass;
    }

    @Override
    public Class getSerializationType() {
        return cacheEntryClass;
    }

    @Override
    public Serializer createSerializer() {
        return new Hibernate52CacheEntrySerializer();
    }

    @Override
    public boolean isOverwritable() {
        return true;
    }
}
