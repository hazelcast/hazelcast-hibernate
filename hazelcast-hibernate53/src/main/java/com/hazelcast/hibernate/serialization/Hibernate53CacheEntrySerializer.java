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

package com.hazelcast.hibernate.serialization;

import com.hazelcast.internal.serialization.impl.SerializationConstants;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;
import org.hibernate.cache.spi.entry.CacheEntry;

import java.io.IOException;
import java.io.Serializable;

/**
 * A {@code CacheEntry} serializer compatible with the SPI interface as updated for Hibernate 5.3. For reference
 * entries the {@code CacheEntry} is serialized directly to avoid relying on too many Hibernate implementation
 * details. Entity entries (the most common type) are serialized by accessing the fields using the interface's
 * methods. Note that the {@code areLazyPropertiesUnfetched()} method was removed in 5.1.
 */
class Hibernate53CacheEntrySerializer implements StreamSerializer<CacheEntry> {
    @Override
    public int getTypeId() {
        return SerializationConstants.HIBERNATE5_TYPE_HIBERNATE_CACHE_ENTRY;
    }

    @Override
    public void destroy() {
    }

    @SuppressWarnings("Duplicates")
    @Override
    public CacheEntry read(final ObjectDataInput in)
            throws IOException {

        try {
            if (in.readBoolean()) {
                return readReference(in);
            }
            return readDisassembled(in);
        } catch (Exception e) {
            throw rethrow(e);
        }
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void write(final ObjectDataOutput out, final CacheEntry object)
            throws IOException {

        try {
            out.writeBoolean(object.isReferenceEntry());
            if (object.isReferenceEntry()) {
                // Reference entries are not disassembled. Instead, to be serialized, they rely entirely on
                // the entity itself being Serializable. This is not a common thing (Hibernate is currently
                // very restrictive about what can be cached by reference), so it may not be worth dealing
                // with at all. This is just a naive implementation relying on the entity's serialization.
                writeReference(out, object);
            } else {
                writeDisassembled(out, object);
            }
        } catch (Exception e) {
            throw rethrow(e);
        }
    }

    @SuppressWarnings("Duplicates")
    private static CacheEntry readDisassembled(final ObjectDataInput in)
            throws IOException {

        final int length = in.readInt();
        final Serializable[] disassembledState = new Serializable[length];
        for (int i = 0; i < length; i++) {
            disassembledState[i] = in.readObject();
        }

        final String subclass = in.readUTF();
        final Object version = in.readObject();

        return new CacheEntryImpl(disassembledState, subclass, version);
    }

    private static CacheEntry readReference(final ObjectDataInput in) throws IOException {
        return ((CacheEntryWrapper) in.readObject()).entry;
    }

    private static IOException rethrow(final Exception e)
            throws IOException {

        if (e instanceof IOException) {
            throw (IOException) e;
        }
        throw new IOException(e);
    }

    @SuppressWarnings("Duplicates")
    private static void writeDisassembled(final ObjectDataOutput out, final CacheEntry object)
            throws IOException {

        final Serializable[] disassembledState = object.getDisassembledState();
        out.writeInt(disassembledState.length);
        for (final Serializable state : disassembledState) {
            out.writeObject(state);
        }

        out.writeUTF(object.getSubclass());
        out.writeObject(object.getVersion());
    }

    private static void writeReference(final ObjectDataOutput out, final CacheEntry object)
            throws IOException {

        out.writeObject(new CacheEntryWrapper(object));
    }

    /**
     * Wraps a CacheEntry so that serializing it will not recursively call back into this class.
     * <p/>
     * {@code CacheEntry} extends {@code Serializable}, so the entry could theoretically just be written with
     * {@code ObjectDataOutput.writeObject(Object)}. However, doing so would cause the {@code SerializationService}
     * to look up the serializer and route the entry right back here again, forming an infinite loop. This wrapper
     * type, which has no explicit mapping, should fall back on the {@code ObjectSerializer} and be serialized by
     * a standard {@code ObjectOutputStream}.
     */
    private static final class CacheEntryWrapper implements Serializable {

        private final CacheEntry entry;

        private CacheEntryWrapper(final CacheEntry entry) {
            this.entry = entry;
        }
    }
}
