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

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;

import static com.hazelcast.nio.UnsafeHelper.UNSAFE;

/**
 * Handles serialization for Hibernate's {@code OldNaturalIdCacheKey}, used for the keys in natural ID regions.
 *
 * @since 3.7
 */
class Hibernate5NaturalIdKeySerializer
        implements StreamSerializer<Object> {

    private static final Class<?> CACHE_KEY_CLASS;
    private static final long ENTITY_NAME_OFFSET;
    private static final long HASH_CODE_OFFSET;
    private static final long NATURAL_ID_VALUES_OFFSET;
    private static final long TENANT_ID_OFFSET;

    static {
        try {
            CACHE_KEY_CLASS = Class.forName("org.hibernate.cache.internal.OldNaturalIdCacheKey");

            Field key = CACHE_KEY_CLASS.getDeclaredField("naturalIdValues");
            NATURAL_ID_VALUES_OFFSET = UNSAFE.objectFieldOffset(key);

            Field type = CACHE_KEY_CLASS.getDeclaredField("entityName");
            ENTITY_NAME_OFFSET = UNSAFE.objectFieldOffset(type);

            Field tenantId = CACHE_KEY_CLASS.getDeclaredField("tenantId");
            TENANT_ID_OFFSET = UNSAFE.objectFieldOffset(tenantId);

            Field hashCode = CACHE_KEY_CLASS.getDeclaredField("hashCode");
            HASH_CODE_OFFSET = UNSAFE.objectFieldOffset(hashCode);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void write(final ObjectDataOutput out, final Object object)
            throws IOException {

        try {
            Serializable[] naturalIdValues = (Serializable[]) UNSAFE.getObject(object, NATURAL_ID_VALUES_OFFSET);
            String entityName = (String) UNSAFE.getObject(object, ENTITY_NAME_OFFSET);
            String tenantId = (String) UNSAFE.getObject(object, TENANT_ID_OFFSET);
            int hashCode = UNSAFE.getInt(object, HASH_CODE_OFFSET);

            out.writeObject(naturalIdValues);
            out.writeUTF(entityName);
            out.writeUTF(tenantId);
            out.writeInt(hashCode);

        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException(e);
        }
    }

    @Override
    public Object read(final ObjectDataInput in)
            throws IOException {

        try {
            Serializable[] key = in.readObject();
            String entityName = in.readUTF();
            String tenantId = in.readUTF();
            int hashCode = in.readInt();

            Object cacheKey = UNSAFE.allocateInstance(CACHE_KEY_CLASS);
            UNSAFE.putObjectVolatile(cacheKey, NATURAL_ID_VALUES_OFFSET, key);
            UNSAFE.putObjectVolatile(cacheKey, ENTITY_NAME_OFFSET, entityName);
            UNSAFE.putObjectVolatile(cacheKey, TENANT_ID_OFFSET, tenantId);
            UNSAFE.putIntVolatile(cacheKey, HASH_CODE_OFFSET, hashCode);
            return cacheKey;

        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException(e);
        }
    }

    @Override
    public int getTypeId() {
        // SerializationConstants.HIBERNATE5_TYPE_HIBERNATE_NATURAL_ID_KEY
        return -206;
    }

    @Override
    public void destroy() {
    }
}
