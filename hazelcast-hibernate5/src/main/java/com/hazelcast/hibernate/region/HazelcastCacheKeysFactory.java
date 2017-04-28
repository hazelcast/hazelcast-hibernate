package com.hazelcast.hibernate.region;

import org.hibernate.cache.spi.CacheKeysFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.entity.EntityPersister;


/**
 * Created by amr on 28.04.2017.
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
