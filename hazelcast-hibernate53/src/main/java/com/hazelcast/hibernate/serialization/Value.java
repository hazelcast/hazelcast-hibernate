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

package com.hazelcast.hibernate.serialization;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;

import java.io.IOException;
import java.util.Comparator;

/**
 * A value within a region cache
 */
public class Value extends Expirable {

    private long timestamp;
    private Object value;

    public Value() {
    }

    public Value(final Object version, final long timestamp, final Object value) {
        super(version);
        this.timestamp = timestamp;
        this.value = value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean isReplaceableBy(final long txTimestamp, final Object newVersion,
                                   final Comparator versionComparator) {
        return version == null
                ? timestamp <= txTimestamp
                : versionComparator.compare(version, newVersion) < 0;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public Object getValue(final long txTimestamp) {
        return timestamp <= txTimestamp ? value : null;
    }

    @Override
    public boolean matches(final ExpiryMarker lock) {
        return false;
    }

    @Override
    public ExpiryMarker markForExpiration(final long timeout, final String nextMarkerId) {
        return new ExpiryMarker(version, timeout, nextMarkerId);
    }

    @Override
    public void readData(final ObjectDataInput in) throws IOException {
        super.readData(in);
        timestamp = in.readLong();
        value = in.readObject();
    }

    @Override
    public void writeData(final ObjectDataOutput out) throws IOException {
        super.writeData(out);
        out.writeLong(timestamp);
        out.writeObject(value);
    }

    @Override
    public int getFactoryId() {
        return HibernateDataSerializerHook.F_ID;
    }

    @Override
    public int getClassId() {
        return HibernateDataSerializerHook.VALUE;
    }

}
