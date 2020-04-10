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
import com.hazelcast.hibernate.serialization.ExpiryMarker;
import com.hazelcast.hibernate.serialization.HibernateDataSerializerHook;
import com.hazelcast.map.EntryProcessor;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;

import java.io.IOException;
import java.util.Map;

/**
 * A concrete implementation of {@link EntryProcessor} which soft-locks
 * a region cached entry
 */
public class LockEntryProcessor implements EntryProcessor<Object, Expirable, Expirable>, IdentifiedDataSerializable {

    private String nextMarkerId;
    private long timeout;
    private Object version;

    public LockEntryProcessor() {
    }

    public LockEntryProcessor(final String nextMarkerId, final long timeout, final Object version) {
        this.nextMarkerId = nextMarkerId;
        this.timeout = timeout;
        this.version = version;
    }

    @Override
    public Expirable process(final Map.Entry<Object, Expirable> entry) {
        Expirable expirable = entry.getValue();

        if (expirable == null) {
            expirable = new ExpiryMarker(version, timeout, nextMarkerId);
        } else {
            expirable = expirable.markForExpiration(timeout, nextMarkerId);
        }

        entry.setValue(expirable);

        return expirable;
    }

    @Override
    public void writeData(final ObjectDataOutput out) throws IOException {
        out.writeUTF(nextMarkerId);
        out.writeLong(timeout);
        out.writeObject(version);
    }

    @Override
    public void readData(final ObjectDataInput in) throws IOException {
        nextMarkerId = in.readUTF();
        timeout = in.readLong();
        version = in.readObject();
    }

    @Override
    public int getFactoryId() {
        return HibernateDataSerializerHook.F_ID;
    }

    @Override
    public int getClassId() {
        return HibernateDataSerializerHook.LOCK;
    }
}
