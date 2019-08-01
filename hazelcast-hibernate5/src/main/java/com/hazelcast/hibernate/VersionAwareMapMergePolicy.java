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

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.spi.merge.MergingValue;
import com.hazelcast.spi.merge.SplitBrainMergePolicy;
import org.hibernate.cache.spi.entry.CacheEntry;
import java.io.IOException;
/**
 * A merge policy implementation to handle split brain remerges based on the timestamps stored in
 * the values.
 */
public class VersionAwareMapMergePolicy implements SplitBrainMergePolicy<Object, MergingValue<Object>> {


    public Object merge(MergingValue<Object> mergingValue, MergingValue<Object> existingValue) {

        final Object mergingVal = mergingValue.getValue();
        final Object existingVal = existingValue.getValue();

        if (existingVal == null) {
            return mergingVal;
        }

        if (existingVal != null && existingVal instanceof CacheEntry
                && mergingVal != null && mergingVal instanceof CacheEntry) {
            CacheEntry existingCacheEntry = (CacheEntry) existingVal;
            CacheEntry mergingCacheEntry = (CacheEntry) mergingVal;
            final Object mergingVersionObject = mergingCacheEntry.getVersion();
            final Object existingVersionObject = existingCacheEntry.getVersion();
            if (mergingVersionObject != null && existingVersionObject != null
                    && mergingVersionObject instanceof Comparable && existingVersionObject instanceof Comparable) {
                final Comparable mergingVersion = (Comparable) mergingVersionObject;
                final Comparable existingVersion = (Comparable) existingVersionObject;
                if (mergingVersion.compareTo(existingVersion) > 0) {
                    return mergingVal;
                } else {
                    return existingVal;
                }
            }
        }
        return mergingVal;
    }

    @Override
    public void writeData(final ObjectDataOutput out) throws IOException {
    }

    @Override
    public void readData(final ObjectDataInput in) throws IOException {
    }
}
