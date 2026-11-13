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

package com.hazelcast.hibernate;

import org.hibernate.cache.spi.ExtendedStatisticsSupport;
import org.hibernate.cache.spi.RegionFactory;
import org.hibernate.cache.spi.support.QueryResultsRegionTemplate;
import org.hibernate.cache.spi.support.StorageAccess;
import org.hibernate.stat.CacheRegionStatistics;

import java.util.function.ToLongFunction;

/**
 * Hazelcast based query results region implementation
 */
class HazelcastQueryResultsRegionImpl extends QueryResultsRegionTemplate implements ExtendedStatisticsSupport {

    HazelcastQueryResultsRegionImpl(final String name, final RegionFactory regionFactory,
                                    final StorageAccess storageAccess) {
        super(name, regionFactory, storageAccess);
    }

    @Override
    public long getElementCountInMemory() {
        return fromStorageAccess(ExtendedStatisticsSupport::getElementCountInMemory);
    }

    @Override
    public long getElementCountOnDisk() {
        return fromStorageAccess(ExtendedStatisticsSupport::getElementCountOnDisk);
    }

    @Override
    public long getSizeInMemory() {
        return fromStorageAccess(ExtendedStatisticsSupport::getSizeInMemory);
    }

    private long fromStorageAccess(final ToLongFunction<ExtendedStatisticsSupport> statistic) {
        final StorageAccess storageAccess = getStorageAccess();
        return storageAccess instanceof ExtendedStatisticsSupport
                ? statistic.applyAsLong((ExtendedStatisticsSupport) storageAccess)
                : CacheRegionStatistics.NO_EXTENDED_STAT_SUPPORT_RETURN;
    }
}
