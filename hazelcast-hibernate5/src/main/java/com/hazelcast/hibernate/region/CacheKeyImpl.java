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

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.DataSerializable;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.type.Type;

/**
 * Cache key implementation
 */
public final class CacheKeyImpl implements DataSerializable {

  private Object id;
  private Type type;
  private String entityOrRoleName;
  private String tenantId;
  private int hashCode;

  public CacheKeyImpl() {
  }

  /**
   * Construct a new key for a collection or entity instance.
   * Note that an entity name should always be the root entity
   * name, not a subclass entity name.
   *
   * @param id The identifier associated with the cached data
   * @param type The Hibernate type mapping
   * @param entityOrRoleName The entity or collection-role name.
   * @param tenantId The tenant identifier associated this data.
   */
  public CacheKeyImpl(
      final Object id,
      final Type type,
      final String entityOrRoleName,
      final String tenantId,
      final SessionFactoryImplementor factory) {
    this.id = id;
    this.type = type;
    this.entityOrRoleName = entityOrRoleName;
    this.tenantId = tenantId;
    this.hashCode = calculateHashCode(type, factory);
  }

  @SuppressWarnings("checkstyle:magicnumber")
  private int calculateHashCode(Type type, SessionFactoryImplementor factory) {
    int result = type.getHashCode(id, factory);
    result = 31 * result + (tenantId != null ? tenantId.hashCode() : 0);
    return result;
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
    out.writeInt(hashCode);
  }

  @Override
  public void readData(ObjectDataInput in) throws IOException {
    id = in.readObject();
    entityOrRoleName = in.readUTF();
    tenantId = in.readUTF();
    type = in.readObject();
    hashCode = in.readInt();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }

    final CacheKeyImpl that = (CacheKeyImpl) other;
    if (!type.isEqual(id, that.id)) {
      return false;
    }
    if (!entityOrRoleName.equals(that.entityOrRoleName)) {
      return false;
    }

    return tenantId != null ? tenantId.equals(that.tenantId) : that.tenantId == null;
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  public String toString() {
    // Used to be required for OSCache
    return entityOrRoleName + '#' + id.toString();
  }
}
