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
 * A concrete implementation of {@link EntryProcessor} which unlocks
 * a soft-locked region cached entry
 */
public class UnlockEntryProcessor implements EntryProcessor<Object, Expirable, Object>, IdentifiedDataSerializable {

    private ExpiryMarker lock;
    private String nextMarkerId;
    private long timestamp;

    public UnlockEntryProcessor() {
    }

    public UnlockEntryProcessor(final ExpiryMarker lock, final String nextMarkerId, final long timestamp) {
        this.lock = lock;
        this.nextMarkerId = nextMarkerId;
        this.timestamp = timestamp;
    }

    @Override
    public Object process(final Map.Entry<Object, Expirable> entry) {
        Expirable expirable = entry.getValue();

        if (expirable != null) {
            if (expirable.matches(lock)) {
                expirable = ((ExpiryMarker) expirable).expire(timestamp);
            } else if (expirable.getValue() != null) {
                // It's a value. Expire the value immediately. This prevents
                // in-flight transactions from adding stale values to the cache
                expirable = new ExpiryMarker(null, timestamp, nextMarkerId).expire(timestamp);
            } else {
                // It's a different marker. Leave it alone.
                return null;
            }
            entry.setValue(expirable);
        }

        return null;
    }

    @Override
    public void writeData(final ObjectDataOutput out) throws IOException {
        out.writeObject(lock);
        out.writeUTF(nextMarkerId);
        out.writeLong(timestamp);
    }

    @Override
    public void readData(final ObjectDataInput in) throws IOException {
        lock = in.readObject();
        nextMarkerId = in.readUTF();
        timestamp = in.readLong();
    }

    @Override
    public int getClassId() {
        return HibernateDataSerializerHook.UNLOCK;
    }

    @Override
    public int getFactoryId() {
        return HibernateDataSerializerHook.F_ID;
    }
}
