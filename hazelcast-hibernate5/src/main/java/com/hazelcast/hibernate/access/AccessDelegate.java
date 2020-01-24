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

package com.hazelcast.hibernate.access;

import com.hazelcast.hibernate.region.HazelcastRegion;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.access.SoftLock;

/**
 * This interface is used to implement basic transactional guarantees
 *
 * @param <T> implementation type of HazelcastRegion
 */
public interface AccessDelegate<T extends HazelcastRegion> {

    /**
     * Get the wrapped cache region
     *
     * @return The underlying region
     */
    T getHazelcastRegion();

    /**
     * Attempt to retrieve an object from the cache. Mainly used in attempting
     * to resolve entities/collections from the second level cache.
     *
     * @param key         The key of the item to be retrieved.
     * @param txTimestamp a timestamp prior to the transaction start time
     * @return the cached object or <tt>null</tt>
     * @throws org.hibernate.cache.CacheException
     *          Propagated from underlying {@link org.hibernate.cache.spi.Region}
     */
    Object get(final Object key, final long txTimestamp) throws CacheException;

    /**
     * Called after an item has been inserted (before the transaction completes),
     * instead of calling evict().
     * This method is used by "synchronous" concurrency strategies.
     *
     * @param key     The item key
     * @param value   The item
     * @param version The item's version value
     * @return Were the contents of the cache actual changed by this operation?
     * @throws org.hibernate.cache.CacheException
     *          Propagated from underlying {@link org.hibernate.cache.spi.Region}
     */
    boolean insert(final Object key, final Object value, final Object version) throws CacheException;

    /**
     * Called after an item has been inserted (after the transaction completes),
     * instead of calling release().
     * This method is used by "asynchronous" concurrency strategies.
     *
     * @param key     The item key
     * @param value   The item
     * @param version The item's version value
     * @return Were the contents of the cache actual changed by this operation?
     * @throws org.hibernate.cache.CacheException
     *          Propagated from underlying {@link org.hibernate.cache.spi.Region}
     */
    boolean afterInsert(final Object key, final Object value, final Object version) throws CacheException;

    /**
     * Called after an item has been updated (before the transaction completes),
     * instead of calling evict(). This method is used by "synchronous" concurrency
     * strategies.
     *
     * @param key             The item key
     * @param value           The item
     * @param currentVersion  The item's current version value
     * @param previousVersion The item's previous version value
     * @return Were the contents of the cache actual changed by this operation?
     * @throws org.hibernate.cache.CacheException
     *          Propagated from underlying {@link org.hibernate.cache.spi.Region}
     */
    boolean update(final Object key, final Object value, final Object currentVersion, final Object previousVersion)
            throws CacheException;

    /**
     * Called after an item has been updated (after the transaction completes),
     * instead of calling release().  This method is used by "asynchronous"
     * concurrency strategies.
     *
     * @param key             The item key
     * @param value           The item
     * @param currentVersion  The item's current version value
     * @param previousVersion The item's previous version value
     * @param lock            The lock previously obtained from {@link #lockItem}
     * @return Were the contents of the cache actual changed by this operation?
     * @throws org.hibernate.cache.CacheException
     *          Propagated from underlying {@link org.hibernate.cache.spi.Region}
     */
    boolean afterUpdate(final Object key, final Object value, final Object currentVersion,
                        final Object previousVersion, final SoftLock lock)
            throws CacheException;

    /**
     * Attempt to cache an object, after loading from the database.
     *
     * @param key         The item key
     * @param value       The item
     * @param txTimestamp a timestamp prior to the transaction start time
     * @param version     the item version number
     * @return <tt>true</tt> if the object was successfully cached
     * @throws org.hibernate.cache.CacheException
     *          Propagated from underlying {@link org.hibernate.cache.spi.Region}
     */
    boolean putFromLoad(final Object key, final Object value, final long txTimestamp, final Object version)
            throws CacheException;

    /**
     * Attempt to cache an object, after loading from the database, explicitly
     * specifying the minimalPut behavior.
     *
     * @param key                The item key
     * @param value              The item
     * @param txTimestamp        a timestamp prior to the transaction start time
     * @param version            the item version number
     * @param minimalPutOverride Explicit minimalPut flag
     * @return <tt>true</tt> if the object was successfully cached
     * @throws org.hibernate.cache.CacheException
     *          Propagated from underlying {@link org.hibernate.cache.spi.Region}
     */
    boolean putFromLoad(final Object key, final Object value, final long txTimestamp, final Object version,
                        final boolean minimalPutOverride) throws CacheException;

    /**
     * Called after an item has become stale (before the transaction completes).
     * This method is used by "synchronous" concurrency strategies.
     *
     * @param key The key of the item to remove
     * @throws org.hibernate.cache.CacheException
     *          Propagated from underlying {@link org.hibernate.cache.spi.Region}
     */
    void remove(final Object key) throws CacheException;

    /**
     * Called to evict data from the entire region
     *
     * @throws org.hibernate.cache.CacheException
     *          Propagated from underlying {@link org.hibernate.cache.spi.Region}
     */
    void removeAll() throws CacheException;

    /**
     * Forcibly evict an item from the cache immediately without regard for transaction
     * isolation.
     *
     * @param key The key of the item to remove
     * @throws org.hibernate.cache.CacheException
     *          Propagated from underlying {@link org.hibernate.cache.spi.Region}
     */
    void evict(final Object key) throws CacheException;

    /**
     * Forcibly evict all items from the cache immediately without regard for transaction
     * isolation.
     *
     * @throws org.hibernate.cache.CacheException
     *          Propagated from underlying {@link org.hibernate.cache.spi.Region}
     */
    void evictAll() throws CacheException;

    /**
     * We are going to attempt to update/delete the keyed object. This
     * method is used by "asynchronous" concurrency strategies.
     * <p/>
     * The returned object must be passed back to release(), to release the
     * lock. Concurrency strategies which do not support client-visible
     * locks may silently return null.
     *
     * @param key     The key of the item to lock
     * @param version The item's current version value
     * @return A representation of our lock on the item; or null.
     * @throws org.hibernate.cache.CacheException
     *          Propagated from underlying {@link org.hibernate.cache.spi.Region}
     */
    SoftLock lockItem(final Object key, final Object version) throws CacheException;

    /**
     * Lock the entire region
     *
     * @return A representation of our lock on the item; or null.
     * @throws org.hibernate.cache.CacheException
     *          Propagated from underlying {@link org.hibernate.cache.spi.Region}
     */
    SoftLock lockRegion() throws CacheException;

    /**
     * Called when we have finished the attempted update/delete (which may or
     * may not have been successful), after transaction completion.  This method
     * is used by "asynchronous" concurrency strategies.
     *
     * @param key  The item key
     * @param lock The lock previously obtained from {@link #lockItem}
     * @throws org.hibernate.cache.CacheException
     *          Propagated from underlying {@link org.hibernate.cache.spi.Region}
     */
    void unlockItem(final Object key, final SoftLock lock) throws CacheException;

    /**
     * Called after we have finished the attempted invalidation of the entire
     * region
     *
     * @param lock The lock previously obtained from {@link #lockRegion}
     * @throws org.hibernate.cache.CacheException
     *          Propagated from underlying {@link org.hibernate.cache.spi.Region}
     */
    void unlockRegion(final SoftLock lock) throws CacheException;
}
