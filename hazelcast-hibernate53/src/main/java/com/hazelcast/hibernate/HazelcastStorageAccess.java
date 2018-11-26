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

package com.hazelcast.hibernate;

import org.hibernate.cache.spi.access.SoftLock;
import org.hibernate.cache.spi.support.DomainDataStorageAccess;

/**
 * Hazelcast specific interface version of Hibernate's DomainDataStorageAccess
 */
public interface HazelcastStorageAccess extends DomainDataStorageAccess {

    void afterUpdate(final Object key, final Object newValue, final Object newVersion);

    void unlockItem(final Object key, final SoftLock lock);

}
