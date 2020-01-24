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

package com.hazelcast.hibernate.region;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.DataSerializable;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.internal.util.ValueHolder;
import org.hibernate.internal.util.compare.EqualsHelper;
import org.hibernate.type.EntityType;
import org.hibernate.type.Type;


/**
 * Cache key implementation for natural ID
 */
public final class NaturalIdCacheKey implements DataSerializable {

  private Serializable[] naturalIdValues;
  private String entityName;
  private String tenantId;
  private int hashCode;
  private transient ValueHolder<String> toString;

  public NaturalIdCacheKey() {
  }

  /**
   * Construct a new key for a caching natural identifier resolutions into the second level cache.
   * @param naturalIdValues The naturalIdValues associated with the cached data
   * @param propertyTypes
   * @param naturalIdPropertyIndexes
   * @param session The originating session
   */
  @SuppressWarnings("checkstyle:magicnumber")
  public NaturalIdCacheKey(
      final Object[] naturalIdValues,
      Type[] propertyTypes, int[] naturalIdPropertyIndexes, final String entityName,
      final SessionImplementor session) {

    this.naturalIdValues = new Serializable[naturalIdValues.length];
    this.entityName = entityName;
    this.tenantId = session.getTenantIdentifier();

    final SessionFactoryImplementor factory = session.getFactory();

    int result = 1;
    result = 31 * result + (this.entityName == null ? 0 : this.entityName.hashCode());
    result = 31 * result + (this.tenantId == null ? 0 : this.tenantId.hashCode());

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
    initTransients();
  }

  private void initTransients() {
    this.toString = new ValueHolder<String>(
        new ValueHolder.DeferredInitializer<String>() {

          @Override
          public String initialize() {
            //Complex toString is needed as naturalIds for entities are not simply based on a single value like primary keys
            //the only same way to differentiate the keys is to included the disassembled values in the string.
            final StringBuilder toStringBuilder = new StringBuilder().append(entityName).append("##NaturalId[");
            for (int i = 0; i < naturalIdValues.length; i++) {
              toStringBuilder.append(naturalIdValues[i]);
              if (i + 1 < naturalIdValues.length) {
                toStringBuilder.append(", ");
              }
            }
            toStringBuilder.append("]");

            return toStringBuilder.toString();
          }
        }
    );
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

    initTransients();
  }

  @Override
  public String toString() {
    return toString.getValue();
  }

  @Override
  public int hashCode() {
    return this.hashCode;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    if (this == o) {
      return true;
    }

    if (hashCode != o.hashCode() || !(o instanceof NaturalIdCacheKey)) {
      //hashCode is part of this check since it is pre-calculated and hash must match for equals to be true
      return false;
    }

    final NaturalIdCacheKey other = (NaturalIdCacheKey) o;
    return EqualsHelper.equals(entityName, other.entityName)
        && EqualsHelper.equals(tenantId, other.tenantId)
        && Arrays.deepEquals(this.naturalIdValues, other.naturalIdValues);
  }
}
