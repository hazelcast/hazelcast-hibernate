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

package com.hazelcast.hibernate.distributed;

import com.hazelcast.hibernate.serialization.Expirable;
import com.hazelcast.hibernate.serialization.HibernateDataSerializerHook;
import com.hazelcast.map.EntryBackupProcessor;
import com.hazelcast.map.EntryProcessor;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;

import java.util.Map;

/**
 * An abstract implementation of {@link EntryProcessor} which acts on a hibernate region cache
 * {@link com.hazelcast.core.IMap}
 */
public abstract class AbstractRegionCacheEntryProcessor implements EntryProcessor<Object, Expirable>,
        EntryBackupProcessor<Object, Expirable>, IdentifiedDataSerializable {

    @Override
    public int getFactoryId() {
        return HibernateDataSerializerHook.F_ID;
    }

    @Override
    public void processBackup(Map.Entry<Object, Expirable> entry) {
        process(entry);
    }

    @Override
    public EntryBackupProcessor<Object, Expirable> getBackupProcessor() {
        return this;
    }

}
