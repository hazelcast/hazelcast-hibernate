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

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.instance.HazelcastInstanceImpl;
import com.hazelcast.instance.HazelcastInstanceProxy;
import com.hazelcast.internal.serialization.SerializationService;
import com.hazelcast.internal.serialization.impl.AbstractSerializationService;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.QuickTest;
import org.hibernate.cache.spi.entry.StandardCacheEntryImpl;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentMap;

import static org.junit.Assert.assertTrue;

@RunWith(HazelcastSerialClassRunner.class)
@Category(QuickTest.class)
public class HibernateSerializationHookAvailableTest {

    private static final Field ORIGINAL;
    private static final Field TYPE_MAP;

    static {
        try {
            ORIGINAL = HazelcastInstanceProxy.class.getDeclaredField("original");
            ORIGINAL.setAccessible(true);

            TYPE_MAP = AbstractSerializationService.class.getDeclaredField("typeMap");
            TYPE_MAP.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @After
    public void teardown() {
        Hazelcast.shutdownAll();
    }

    @Test
    public void testAutoregistrationOnHibernate5Available()
            throws Exception {

        HazelcastInstance hz = Hazelcast.newHazelcastInstance();
        HazelcastInstanceImpl impl = (HazelcastInstanceImpl) ORIGINAL.get(hz);
        SerializationService ss = impl.getSerializationService();
        @SuppressWarnings("unchecked")
        ConcurrentMap<Class, ?> typeMap = (ConcurrentMap) TYPE_MAP.get(ss);

        boolean cacheKeySerializerFound = false;
        boolean cacheEntrySerializerFound = false;
        boolean naturalIdKeySerializerFound = false;
        for (Class clazz : typeMap.keySet()) {
            // The Old* implementations are matched by class name here just to avoid the Reflection hassle
            // of retrieving their classes, since they're package-private
            if ("org.hibernate.cache.internal.OldCacheKeyImplementation".equals(clazz.getName())) {
                cacheKeySerializerFound = true;
            } else if ("org.hibernate.cache.internal.OldNaturalIdCacheKey".equals(clazz.getName())) {
                naturalIdKeySerializerFound = true;
            } else if (StandardCacheEntryImpl.class.equals(clazz)) {
                cacheEntrySerializerFound = true;
            }
        }

        assertTrue("CacheKey serializer not found", cacheKeySerializerFound);
        assertTrue("CacheEntry serializer not found", cacheEntrySerializerFound);
        assertTrue("NaturalIdCacheKey serializer not found", naturalIdKeySerializerFound);
    }
}
