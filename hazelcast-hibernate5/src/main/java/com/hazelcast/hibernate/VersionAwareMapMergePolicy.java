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

import com.hazelcast.spi.merge.MergingValue;
import com.hazelcast.spi.merge.SplitBrainMergePolicy;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import org.hibernate.cache.spi.entry.CacheEntry;

import java.io.IOException;

/**
 * A merge policy implementation to handle split brain remerges based on the timestamps stored in
 * the values.
 */
public class VersionAwareMapMergePolicy implements SplitBrainMergePolicy<Object, MergingValue<Object>> {

    @Override
    public Object merge(MergingValue<Object> mergingVal, MergingValue<Object> existingVal) {
        final Object existingValue = existingVal != null ? existingVal.getValue() : null;
        final Object mergingValue = mergingVal != null ? mergingVal.getValue() : null;
        if (existingValue instanceof CacheEntry && mergingValue instanceof CacheEntry) {

            final CacheEntry existingCacheEntry = (CacheEntry) existingValue;
            final CacheEntry mergingCacheEntry = (CacheEntry) mergingValue;
            final Object mergingVersionObject = mergingCacheEntry.getVersion();
            final Object existingVersionObject = existingCacheEntry.getVersion();
            if (mergingVersionObject instanceof Comparable && existingVersionObject instanceof Comparable) {

                final Comparable mergingVersion = (Comparable) mergingVersionObject;
                final Comparable existingVersion = (Comparable) existingVersionObject;

                if (mergingVersion.compareTo(existingVersion) > 0) {
                    return mergingValue;
                } else {
                    return existingValue;
                }
            }
        }
        return mergingValue;
    }

    @Override
    public void writeData(ObjectDataOutput out) throws IOException {
    }

    @Override
    public void readData(ObjectDataInput in) throws IOException {
    }
}
