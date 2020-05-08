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

package com.hazelcast.hibernate.local;

import com.hazelcast.hibernate.serialization.HibernateDataSerializerHook;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;

import java.io.IOException;
import java.util.UUID;

/**
 * Hazelcast compatible implementation of a timestamp for internal eviction
 */
public class Timestamp implements IdentifiedDataSerializable {

    private Object key;
    private long timestamp;
    private UUID senderId;

    public Timestamp() {
    }

    public Timestamp(final Object key, final long timestamp, final UUID senderId) {
        this.key = key;
        this.timestamp = timestamp;
        this.senderId = senderId;
    }

    public Object getKey() {
        return key;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public UUID getSenderId() {
        return senderId;
    }

    @Override
    public void writeData(final ObjectDataOutput out) throws IOException {
        out.writeObject(key);
        out.writeLong(timestamp);
        out.writeUTF(senderId.toString());
    }

    @Override
    public void readData(final ObjectDataInput in) throws IOException {
        key = in.readObject();
        timestamp = in.readLong();
        senderId = UUID.fromString(in.readUTF());
    }

    @Override
    public int getFactoryId() {
        return HibernateDataSerializerHook.F_ID;
    }

    @Override
    public int getClassId() {
        return HibernateDataSerializerHook.TIMESTAMP;
    }

    @Override
    public String toString() {
        return "Timestamp{ key=" + key + ", timestamp=" + timestamp + ", senderId=" + senderId + '}';
    }
}
