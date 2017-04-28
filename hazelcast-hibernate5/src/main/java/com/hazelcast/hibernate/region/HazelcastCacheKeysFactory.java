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

import org.hibernate.cache.spi.CacheKeysFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.entity.EntityPersister;


/**
 * Cache key factory.
 */
public class HazelcastCacheKeysFactory implements CacheKeysFactory {

  @Override
  public Object createCollectionKey(Object id, CollectionPersister persister, SessionFactoryImplementor factory,
      String tenantId) {
    return new CacheKeyImpl(id, persister.getKeyType(), persister.getRole(), tenantId, factory);
  }

  @Override
  public Object createEntityKey(Object id, EntityPersister persister, SessionFactoryImplementor factory, String tenantId) {
    return new CacheKeyImpl(id, persister.getIdentifierType(), persister.getRootEntityName(), tenantId, factory);
  }

  @Override
  public Object createNaturalIdKey(Object[] naturalIdValues, EntityPersister persister, SessionImplementor session) {
    return new NaturalIdCacheKey(naturalIdValues, persister.getPropertyTypes(), persister.getNaturalIdentifierProperties(),
        persister.getRootEntityName(), session);
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
