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

package com.hazelcast.hibernate;

import org.hibernate.cache.cfg.spi.CollectionDataCachingConfig;
import org.hibernate.cache.cfg.spi.DomainDataRegionBuildingContext;
import org.hibernate.cache.cfg.spi.DomainDataRegionConfig;
import org.hibernate.cache.cfg.spi.EntityDataCachingConfig;
import org.hibernate.cache.cfg.spi.NaturalIdDataCachingConfig;
import org.hibernate.cache.spi.CacheKeysFactory;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cache.spi.access.CollectionDataAccess;
import org.hibernate.cache.spi.access.EntityDataAccess;
import org.hibernate.cache.spi.access.NaturalIdDataAccess;
import org.hibernate.cache.spi.access.SoftLock;
import org.hibernate.cache.spi.support.CollectionReadWriteAccess;
import org.hibernate.cache.spi.support.CollectionTransactionAccess;
import org.hibernate.cache.spi.support.DomainDataRegionImpl;
import org.hibernate.cache.spi.support.DomainDataStorageAccess;
import org.hibernate.cache.spi.support.EntityReadWriteAccess;
import org.hibernate.cache.spi.support.EntityTransactionalAccess;
import org.hibernate.cache.spi.support.NaturalIdReadWriteAccess;
import org.hibernate.cache.spi.support.NaturalIdTransactionalAccess;
import org.hibernate.cache.spi.support.RegionFactoryTemplate;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

/**
 * Hazelcast based timestamp using region implementation
 */
public class HazelcastDomainDataRegionImpl extends DomainDataRegionImpl {

    HazelcastDomainDataRegionImpl(final DomainDataRegionConfig regionConfig,
                                  final RegionFactoryTemplate regionFactory,
                                  final DomainDataStorageAccess domainDataStorageAccess,
                                  final CacheKeysFactory defaultKeysFactory,
                                  final DomainDataRegionBuildingContext buildingContext) {
        super(regionConfig, regionFactory, domainDataStorageAccess, defaultKeysFactory, buildingContext);
    }

    @Override
    public CollectionDataAccess generateCollectionAccess(final CollectionDataCachingConfig accessConfig) {
        if (accessConfig.getAccessType() == AccessType.READ_WRITE) {
            return generateReadWriteCollectionAccess(accessConfig);
        } else {
            return super.generateCollectionAccess(accessConfig);
        }
    }

    @Override
    protected EntityDataAccess generateReadWriteEntityAccess(final EntityDataCachingConfig accessConfig) {
        return new EntityReadWriteAccess(this, getEffectiveKeysFactory(), getCacheStorageAccess(), accessConfig) {
            @Override
            public boolean afterUpdate(final SharedSessionContractImplementor session, final Object key,
                                       final Object value, final Object currentVersion, final Object previousVersion,
                                       final SoftLock lock) {
                final boolean result = super.afterUpdate(session, key, value, currentVersion, previousVersion, lock);
                ((HazelcastStorageAccess) getStorageAccess()).afterUpdate(key, value, currentVersion);
                return result;
            }

            @Override
            public void unlockItem(final SharedSessionContractImplementor session, final Object key,
                                   final SoftLock lock) {
                super.unlockItem(session, key, lock);
                ((HazelcastStorageAccess) getStorageAccess()).unlockItem(key, lock);
            }
        };
    }

    @Override
    protected NaturalIdDataAccess generateReadWriteNaturalIdAccess(final NaturalIdDataCachingConfig accessConfig) {
        return new NaturalIdReadWriteAccess(this, getEffectiveKeysFactory(), getCacheStorageAccess(), accessConfig) {
            @Override
            public boolean afterUpdate(final SharedSessionContractImplementor session, final Object key,
                                       final Object value, final SoftLock lock) {
                final boolean result = super.afterUpdate(session, key, value, lock);
                ((HazelcastStorageAccess) getStorageAccess()).afterUpdate(key, value, null);
                return result;
            }

            @Override
            public void unlockItem(final SharedSessionContractImplementor session, final Object key,
                                   final SoftLock lock) {
                super.unlockItem(session, key, lock);
                ((HazelcastStorageAccess) getStorageAccess()).unlockItem(key, lock);
            }
        };
    }

    @Override
    protected CollectionDataAccess generateTransactionalCollectionDataAccess(
            final CollectionDataCachingConfig accessConfig) {
        return new CollectionTransactionAccess(this, getEffectiveKeysFactory(), getCacheStorageAccess(), accessConfig) {
            @Override
            public void unlockItem(final SharedSessionContractImplementor session, final Object key,
                                   final SoftLock lock) {
                super.unlockItem(session, key, lock);
                ((HazelcastStorageAccess) getStorageAccess()).unlockItem(key, lock);
            }
        };
    }

    @Override
    protected EntityDataAccess generateTransactionalEntityDataAccess(final EntityDataCachingConfig accessConfig) {
        return new EntityTransactionalAccess(this, getEffectiveKeysFactory(), getCacheStorageAccess(), accessConfig) {
            @Override
            public boolean afterUpdate(final SharedSessionContractImplementor session, final Object key,
                                       final Object value, final Object currentVersion, final Object previousVersion,
                                       final SoftLock lock) {
                final boolean result = super.afterUpdate(session, key, value, currentVersion, previousVersion, lock);
                ((HazelcastStorageAccess) getStorageAccess()).afterUpdate(key, value, currentVersion);
                return result;
            }

            @Override
            public void unlockItem(final SharedSessionContractImplementor session, final Object key,
                                   final SoftLock lock) {
                super.unlockItem(session, key, lock);
                ((HazelcastStorageAccess) getStorageAccess()).unlockItem(key, lock);
            }
        };
    }

    @Override
    protected NaturalIdDataAccess generateTransactionalNaturalIdDataAccess(NaturalIdDataCachingConfig accessConfig) {
        return new NaturalIdTransactionalAccess(this, getEffectiveKeysFactory(), getCacheStorageAccess(), accessConfig) {
            @Override
            public boolean afterUpdate(final SharedSessionContractImplementor session, final Object key,
                                       final Object value, final SoftLock lock) {
                final boolean result = super.afterUpdate(session, key, value, lock);
                ((HazelcastStorageAccess) getStorageAccess()).afterUpdate(key, value, null);
                return result;
            }

            @Override
            public void unlockItem(final SharedSessionContractImplementor session, final Object key,
                                   final SoftLock lock) {
                super.unlockItem(session, key, lock);
                ((HazelcastStorageAccess) getStorageAccess()).unlockItem(key, lock);
            }
        };
    }

    private CollectionDataAccess generateReadWriteCollectionAccess(final CollectionDataCachingConfig accessConfig) {
        return new CollectionReadWriteAccess(this, getEffectiveKeysFactory(), getCacheStorageAccess(), accessConfig) {
            @Override
            public void unlockItem(final SharedSessionContractImplementor session, final Object key,
                                   final SoftLock lock) {
                super.unlockItem(session, key, lock);
                ((HazelcastStorageAccess) getStorageAccess()).unlockItem(key, lock);
            }
        };
    }
}
