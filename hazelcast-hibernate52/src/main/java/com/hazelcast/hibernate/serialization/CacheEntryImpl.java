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

import org.hibernate.cache.spi.entry.CacheEntry;

import java.io.Serializable;

/**
 * Simple CacheEntry implementation to avoid using internal Hibernate class StandardCacheEntryImpl
 */
public class CacheEntryImpl implements CacheEntry {
    private final Serializable[] disassembledState;
    private final String subclass;
    private final Object version;

    public CacheEntryImpl(Serializable[] disassembledState, String subclass, Object version) {
        this.disassembledState = disassembledState;
        this.subclass = subclass;
        this.version = version;
    }

    @Override
    public boolean isReferenceEntry() {
        return false;
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
}
