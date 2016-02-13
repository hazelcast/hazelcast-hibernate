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
import java.lang.reflect.Field;

import static com.hazelcast.nio.UnsafeHelper.UNSAFE;

/**
 * The actual CacheKey serializer implementation
 *
 * @since 3.7
 */
class Hibernate5CacheKeySerializer
        implements StreamSerializer<Object> {

    private static final Class<?> CACHE_KEY_CLASS;
    private static final long KEY_OFFSET;
    private static final long TYPE_OFFSET;
    private static final long ENTITY_OR_ROLE_NAME_OFFSET;
    private static final long TENANT_ID_OFFSET;
    private static final long HASH_CODE_OFFSET;

    static {
        try {
            CACHE_KEY_CLASS = Class.forName("org.hibernate.cache.internal.OldCacheKeyImplementation");

            Field key = CACHE_KEY_CLASS.getDeclaredField("id");
            KEY_OFFSET = UNSAFE.objectFieldOffset(key);

            Field type = CACHE_KEY_CLASS.getDeclaredField("type");
            TYPE_OFFSET = UNSAFE.objectFieldOffset(type);

            Field entityOrRoleName = CACHE_KEY_CLASS.getDeclaredField("entityOrRoleName");
            ENTITY_OR_ROLE_NAME_OFFSET = UNSAFE.objectFieldOffset(entityOrRoleName);

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
            Object key = UNSAFE.getObject(object, KEY_OFFSET);
            Object type = UNSAFE.getObject(object, TYPE_OFFSET);
            String entityOrRoleName = (String) UNSAFE.getObject(object, ENTITY_OR_ROLE_NAME_OFFSET);
            String tenantId = (String) UNSAFE.getObject(object, TENANT_ID_OFFSET);
            int hashCode = UNSAFE.getInt(object, HASH_CODE_OFFSET);

            out.writeObject(key);
            out.writeObject(type);
            out.writeUTF(entityOrRoleName);
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
            Object key = in.readObject();
            Object type = in.readObject();
            String entityOrRoleName = in.readUTF();
            String tenantId = in.readUTF();
            int hashCode = in.readInt();

            Object cacheKey = UNSAFE.allocateInstance(CACHE_KEY_CLASS);
            UNSAFE.putObjectVolatile(cacheKey, KEY_OFFSET, key);
            UNSAFE.putObjectVolatile(cacheKey, TYPE_OFFSET, type);
            UNSAFE.putObjectVolatile(cacheKey, ENTITY_OR_ROLE_NAME_OFFSET, entityOrRoleName);
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
        // SerializationConstants.HIBERNATE5_TYPE_HIBERNATE_CACHE_KEY
        return -204;
    }

    @Override
    public void destroy() {
    }
}
