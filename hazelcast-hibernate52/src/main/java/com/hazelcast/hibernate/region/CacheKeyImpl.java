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

package com.hazelcast.hibernate.region;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.DataSerializable;
import org.hibernate.type.Type;

import java.io.IOException;


/**
 * Cache key implementation
 */
public class CacheKeyImpl implements DataSerializable {
    private Object id;
    private String entityOrRoleName;
    private String tenantId;
    private Type type;

    public CacheKeyImpl() {
    }

    public CacheKeyImpl(Object id, String entityOrRoleName, String tenantId, Type type) {
        this.id = id;
        this.entityOrRoleName = entityOrRoleName;
        this.tenantId = tenantId;
        this.type = type;
    }

    Object getId() {
        return id;
    }

    @Override
    public void writeData(ObjectDataOutput out) throws IOException {
        out.writeObject(id);
        out.writeUTF(entityOrRoleName);
        out.writeUTF(tenantId);
        out.writeObject(type);
    }

    @Override
    public void readData(ObjectDataInput in) throws IOException {
        id = in.readObject();
        entityOrRoleName = in.readUTF();
        tenantId = in.readUTF();
        type = in.readObject();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CacheKeyImpl cacheKey = (CacheKeyImpl) o;

        if (!type.isEqual(id, cacheKey.id)) {
            return false;
        }
        if (!entityOrRoleName.equals(cacheKey.entityOrRoleName)) {
            return false;
        }
        return tenantId != null ? tenantId.equals(cacheKey.tenantId) : cacheKey.tenantId == null;
    }

    @Override
    public int hashCode() {
        int result = type.getHashCode(id);
        result = 31 * result + entityOrRoleName.hashCode();
        result = 31 * result + (tenantId != null ? tenantId.hashCode() : 0);
        return result;
    }
}
