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

import org.hibernate.cache.spi.entry.CacheEntry;

import java.io.Serializable;

/**
 * Simple CacheEntry implementation to avoid using internal Hibernate class StandardCacheEntryImpl
 */
public class CacheEntryImpl implements CacheEntry {
    private final Serializable[] disassembledState;
    private final String subclass;
    private final Object version;

    public CacheEntryImpl(final Serializable[] disassembledState, final String subclass, final Object version) {
        this.disassembledState = disassembledState;
        this.subclass = subclass;
        this.version = version;
    }

    @Override
    public String getSubclass() {
        return subclass;
    }

    @Override
    public Object getVersion() {
        return version;
    }

    @Override
    public Serializable[] getDisassembledState() {
        return disassembledState;
    }

    @Override
    public boolean isReferenceEntry() {
        return false;
    }
}
