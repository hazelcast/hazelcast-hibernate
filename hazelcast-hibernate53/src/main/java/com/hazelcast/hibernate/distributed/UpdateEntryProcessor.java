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
import com.hazelcast.hibernate.serialization.Value;
import com.hazelcast.map.EntryProcessor;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;

import java.io.IOException;
import java.util.Map;

/**
 * A concrete implementation of {@link EntryProcessor} which attempts
 * to update a region cache entry
 */
public class UpdateEntryProcessor implements EntryProcessor<Object, Expirable, Boolean>, IdentifiedDataSerializable {

    private ExpiryMarker lock;
    private Object newValue;
    private Object newVersion;
    private String nextMarkerId;
    private long timestamp;

    public UpdateEntryProcessor() {
    }

    public UpdateEntryProcessor(final ExpiryMarker lock, final Object newValue, final Object newVersion,
                                final String nextMarkerId, final long timestamp) {
        this.lock = lock;
        this.nextMarkerId = nextMarkerId;
        this.newValue = newValue;
        this.newVersion = newVersion;
        this.timestamp = timestamp;
    }

    @Override
    public Boolean process(final Map.Entry<Object, Expirable> entry) {
        Expirable expirable = entry.getValue();
        boolean updated;

        if (expirable == null) {
            // Nothing there. The entry was evicted? It should be safe to replace it
            expirable = new Value(newVersion, timestamp, newValue);
            updated = true;
        } else {
            if (expirable.matches(lock)) {
                final ExpiryMarker marker = (ExpiryMarker) expirable;
                if (marker.isConcurrent()) {
                    // Multiple transactions are attempting to update the same entry. Its highly
                    // likely that the value we are attempting to set is invalid. Instead just
                    // expire the entry and allow the next put to the cache to succeed if no more
                    // transactions are in-flight.
                    expirable = marker.expire(timestamp);
                    updated = false;
                } else {
                    // Only one transaction attempted to update the entry so it is safe to replace
                    // it with the value supplied
                    expirable = new Value(newVersion, timestamp, newValue);
                    updated = true;
                }
            } else if (expirable.getValue() == null) {
                // It's a different marker, Leave it as is
                return false;
            } else {
                // It's a value. We have no way to see which is correct so we expire the entry.
                // It is expired instead of removed to prevent in progress transactions from
                // putting stale values into the cache
                expirable = new ExpiryMarker(newVersion, timestamp, nextMarkerId).expire(timestamp);
                updated = false;
            }
        }

        entry.setValue(expirable);
        return updated;
    }

    @Override
    public void writeData(final ObjectDataOutput out) throws IOException {
        out.writeObject(lock);
        out.writeObject(newValue);
        out.writeObject(newVersion);
        out.writeUTF(nextMarkerId);
        out.writeLong(timestamp);
    }

    @Override
    public void readData(final ObjectDataInput in) throws IOException {
        lock = in.readObject();
        newValue = in.readObject();
        newVersion = in.readObject();
        nextMarkerId = in.readUTF();
        timestamp = in.readLong();
    }

    @Override
    public int getClassId() {
        return HibernateDataSerializerHook.UPDATE;
    }

    @Override
    public int getFactoryId() {
        return HibernateDataSerializerHook.F_ID;
    }
}
