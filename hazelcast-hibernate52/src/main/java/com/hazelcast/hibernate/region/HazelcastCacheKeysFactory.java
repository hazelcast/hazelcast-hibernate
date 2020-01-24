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

import org.hibernate.cache.spi.CacheKeysFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.entity.EntityPersister;


/**
 * Cache key factory.
 */
public class HazelcastCacheKeysFactory implements CacheKeysFactory {

  @Override
  public Object createCollectionKey(Object id, CollectionPersister persister, SessionFactoryImplementor factory,
      String tenantIdentifier) {
    return new CacheKeyImpl(id, persister.getKeyType(), persister.getRole(), tenantIdentifier, factory);
  }

  @Override
  public Object createEntityKey(Object id, EntityPersister persister, SessionFactoryImplementor factory,
      String tenantIdentifier) {
    return new CacheKeyImpl(id, persister.getIdentifierType(), persister.getRootEntityName(), tenantIdentifier, factory);
  }

  @Override
  public Object createNaturalIdKey(Object[] naturalIdValues, EntityPersister persister,
      SharedSessionContractImplementor session) {
    return new NaturalIdCacheKey(naturalIdValues,  persister.getPropertyTypes(), persister.getNaturalIdentifierProperties(),
        persister.getRootEntityName(), (SessionImplementor) session);
  }

  @Override
  public Object getEntityId(Object cacheKey) {
    return ((CacheKeyImpl) cacheKey).getId();
  }

  @Override
  public Object getCollectionId(Object cacheKey) {
    return ((CacheKeyImpl) cacheKey).getId();
  }

  @Override
  public Object[] getNaturalIdValues(Object cacheKey) {
    return ((NaturalIdCacheKey) cacheKey).getNaturalIdValues();
  }
}
