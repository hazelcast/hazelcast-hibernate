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
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.EntityType;
import org.hibernate.type.Type;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;

/**
 * Cache key implementation for natural ID
 */
public class NaturalIdCacheKey implements DataSerializable {

    private Serializable[] naturalIdValues;
    private String entityName;
    private String tenantId;
    private int hashCode;

    public NaturalIdCacheKey() {
    }

    @SuppressWarnings("checkstyle:magicnumber")
    public NaturalIdCacheKey(final Object[] naturalIdValues, Type[] propertyTypes, int[] naturalIdPropertyIndexes,
                             final String entityName, final SharedSessionContractImplementor session) {

        this.naturalIdValues = new Serializable[naturalIdValues.length];
        this.entityName = entityName;
        this.tenantId = session.getTenantIdentifier();

        SessionFactoryImplementor factory = session.getFactory();

        int result = 1;
        result = 31 * result + (entityName != null ? entityName.hashCode() : 0);
        result = 31 * result + (tenantId != null ? tenantId.hashCode() : 0);

        for (int i = 0; i < naturalIdValues.length; i++) {
            final int naturalIdPropertyIndex = naturalIdPropertyIndexes[i];
            final Type type = propertyTypes[naturalIdPropertyIndex];
            final Object value = naturalIdValues[i];

            result = 31 * result + (value != null ? type.getHashCode(value, factory) : 0);

            if (type instanceof EntityType && type.getSemiResolvedType(factory).getReturnedClass().isInstance(value)) {
                this.naturalIdValues[i] = (Serializable) value;
            } else {
                this.naturalIdValues[i] = type.disassemble(value, session, null);
            }
        }
        this.hashCode = result;
    }

    Serializable[] getNaturalIdValues() {
        return naturalIdValues;
    }

    @Override
    public void writeData(ObjectDataOutput out) throws IOException {
        out.writeInt(naturalIdValues.length);
        for (Serializable naturalIdValue : naturalIdValues) {
            out.writeObject(naturalIdValue);
        }
        out.writeUTF(entityName);
        out.writeUTF(tenantId);
        out.writeInt(hashCode);
    }

    @Override
    public void readData(ObjectDataInput in) throws IOException {
        int length = in.readInt();
        naturalIdValues = new Serializable[length];
        for (int i = 0; i < length; i++) {
            naturalIdValues[i] = in.readObject();
        }
        entityName = in.readUTF();
        tenantId = in.readUTF();
        hashCode = in.readInt();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        NaturalIdCacheKey that = (NaturalIdCacheKey) o;

        if (!Arrays.deepEquals(naturalIdValues, that.naturalIdValues)) {
            return false;
        }
        if (entityName != null ? !entityName.equals(that.entityName) : that.entityName != null) {
            return false;
        }
        return tenantId != null ? tenantId.equals(that.tenantId) : that.tenantId == null;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
