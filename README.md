# Hibernate Second Level Cache

Hazelcast provides a distributed second-level cache for your Hibernate entities, collections, and queries.

You can use an `IMap` as distributed storage(`HazelcastCacheRegionFactory`), or ConcurrentHashMap-based near-cache(`HazelcastLocalCacheRegionFactory`) with updates synchronized via `ITopic`.

## Supported Versions

`hazelcast-hibernate5` supports Hibernate 5.0.x, Hibernate 5.1.x and Hazelcast 3.7+
`hazelcast-hibernate52` supports Hibernate 5.2.x and Hazelcast 3.7+
`hazelcast-hibernate53` supports Hibernate 5.3.x/5.4.x and Hazelcast 3.7+

## Documentation

# Table of Contents

* [Hibernate Second Level Cache](#hibernate-second-level-cache)
  * [Supported Hibernate and Hazelcast Versions](#supported-hibernate-and-hazelcast-versions)
  * [Configuring Hibernate for Hazelcast](#configuring-hibernate-for-hazelcast)
    * [Enabling Second Level Cache](#enabling-second-level-cache)
    * [Configuring RegionFactory](#configuring-regionfactory)
      * [HazelcastCacheRegionFactory](#hazelcastcacheregionfactory)
      * [HazelcastLocalCacheRegionFactory](#hazelcastlocalcacheregionfactory)
    * [Configuring Query Cache and Other Settings](#configuring-query-cache-and-other-settings)
  * [Configuring Hazelcast for Hibernate](#configuring-hazelcast-for-hibernate)
  * [Setting P2P for Hibernate](#setting-p2p-for-hibernate)
  * [Setting Client/Server for Hibernate](#setting-client-server-for-hibernate)
  * [Configuring Cache Concurrency Strategy](#configuring-cache-concurrency-strategy)
  * [Advanced Settings](#advanced-settings)


## Configuring Hibernate for Hazelcast

To configure Hibernate for Hazelcast:

- Add the jar to your classpath (depending on your Hibernate/Hazelcast versions)
- Enable Second-Level Cache
- Choose a desired `RegionFactory` implementation
- Configure remaining properties by:
    - Adding them to your Hibernate configuration file, e.g., `hibernate.cfg.xml`
    - Adding them to Spring Boot's `application.properties` prefixed with `spring.jpa.properties`, e.g., `spring.jpa.properties.hibernate.cache.use_second_level_cache=true`

### Enabling Second Level Cache

```xml
<property name="hibernate.cache.use_second_level_cache">true</property>
```

### Configuring RegionFactory

You can configure Hibernate RegionFactory with `HazelcastCacheRegionFactory` or `HazelcastLocalCacheRegionFactory`.

#### HazelcastCacheRegionFactory

`HazelcastCacheRegionFactory` uses standard Hazelcast distributed maps to cache the data, so all cache operations go through the wire.

```xml    
<property name="hibernate.cache.region.factory_class">
   com.hazelcast.hibernate.HazelcastCacheRegionFactory
</property>
```

All operations like `get`, `put`, and `remove` will be performed on a distributed map. The only downside of using `HazelcastCacheRegionFactory` may be lower performance compared to `HazelcastLocalCacheRegionFactory` since operations are handled as distributed calls.

***NOTE:*** *If you use `HazelcastCacheRegionFactory`, you can see your maps on [Management Center](http://docs.hazelcast.org/docs/management-center/latest/manual/html/index.html).*

With `HazelcastCacheRegionFactory`, all below caches are distributed across Hazelcast Cluster:

- Entity Cache
- Collection Cache
- Timestamp Cache

#### HazelcastLocalCacheRegionFactory

You can use `HazelcastLocalCacheRegionFactory`, which stores data in a local member and sends invalidation messages when an entry is changed locally.

```xml
<property name="hibernate.cache.region.factory_class">
  com.hazelcast.hibernate.HazelcastLocalCacheRegionFactory
</property>
```

With `HazelcastLocalCacheRegionFactory`, each cluster member has a local map, and each of them is registered to a Hazelcast Topic (ITopic). 
Whenever a `put` or `remove` operation is performed on a member, `hazelcast-hibernate` sends an invalidation message to other members, 
which removes related entries from their local storage. 

In the `get` operations, invalidation messages are not generated, and reads are performed on the local map.

An illustration of the above logic is shown below:

![Invalidation with Local Cache Region Factory](images/HZLocalCacheRgnFactory.jpg)

If your operations consist mostly of reads, then this option gives better performance.

***NOTE:*** *If you use `HazelcastLocalCacheRegionFactory`, you cannot see your maps on [Management Center](https://docs.hazelcast.org/docs/management-center/latest/manual/html/index.html).*

With `HazelcastLocalCacheRegionFactory`, all of the following caches are not distributed and are kept locally in the Hazelcast member:

- Entity Cache
- Collection Cache
- Timestamp Cache

_Entity_ and _Collection_ caches are invalidated on update. When they are updated on a member, an invalidation message is sent to all other members in order to remove the entity from their local cache. When needed, each member reads that data from the underlying datasource. 

On every _Timestamp_ cache update, `hazelcast-hibernate` publishes an invalidation message to a topic (see #hazelcastlocalcacheregionfactory for details).

Eviction support is limited to the maximum size of the map (defined by `max-size` configuration element) and TTL only. When maximum size is hit, 20% of the entries will be evicted automatically.

### Configuring Query Cache and Other Settings

- To enable use of query cache:

    ```xml
    <property name="hibernate.cache.use_query_cache">true</property>
    ```

- To force minimal puts into query cache:

    ```xml
    <property name="hibernate.cache.use_minimal_puts">true</property>
    ```

- To avoid `NullPointerException` when you have entities that have composite keys (using `@IdClass`):

    ```xml
    <property name="hibernate.session_factory_name">yourFactoryName</property>
    ```
    
***NOTE:*** *QueryCache is always LOCAL to the member and never distributed across Hazelcast Cluster.*

## Configuring Hazelcast for Hibernate

To configure Hazelcast for Hibernate, put the configuration file named `hazelcast.xml` into the root of your classpath. If Hazelcast cannot find `hazelcast.xml`, then it will use the default configuration.

You can define a custom-named Hazelcast configuration XML file with one of these Hibernate configuration properties. 

```xml
<property name="hibernate.cache.provider_configuration_file_resource_path">
  hazelcast-custom-config.xml
</property>
```


```xml
<property name="hibernate.cache.hazelcast.configuration_file_path">
  hazelcast-custom-config.xml
</property>
```

If you're using Hazelcast client (`hibernate.cache.hazelcast.use_native_client=true`), you can specify a custom Hazelcast client configuration file by using the same parameters.

Hazelcast creates a separate distributed map for each Hibernate cache region. You can easily configure these regions via Hazelcast map configuration. You can define **backup**, **eviction**, **TTL** and **Near Cache** properties.

## Using Second-Level Cache in Peer-to-Peer mode

Hibernate Second Level Cache can use Hazelcast in two modes: Peer-to-Peer (P2P) and Client/Server (next section).

When using the _Peer-to-Peer_ mode, each Hibernate deployment launches its Hazelcast instance. 

However, there's an option to configure Hibernate to use an existing instance instead of creating a new `HazelcastInstance` for each `SessionFactory`.
 
To achieve this, set the `hibernate.cache.hazelcast.instance_name` Hibernate property to the `HazelcastInstance`'s name. 

For more information, please see <a href="http://docs.hazelcast.org/docs/latest-dev/manual/html-single/index.html#binding-to-a-named-instance" target="_blank">Named Instance Scope</a>

**Disabling shutdown during SessionFactory.close()**

You can disable shutting down `HazelcastInstance` during `SessionFactory.close()`. To do this, set the Hibernate property `hibernate.cache.hazelcast.shutdown_on_session_factory_close` to false. *(In this case, you should not set the Hazelcast property `hazelcast.shutdownhook.enabled` to false.)* The default value is `true`.


## Using Second-Level Cache in Client/Server mode

You can set up Hazelcast to connect to the cluster as Native Client. 

The native client is not a member; it connects to one of the cluster members and delegates all cluster-wide operations to it. 

A client instance started in the Native Client mode uses smart routing: when the related cluster member dies, the client transparently switches to another live member. 

All client operations are retry-able, meaning that the client resends the request as many as ten times in case of a failure. 

After the 10th retry, it throws an exception. You cannot change the routing mode and retry-able operation configurations of the Native Client instance used by Hibernate 2nd Level Cache. 

Please see the <a href="http://docs.hazelcast.org/docs/latest/manual/html-single/index.html#setting-smart-routing" target="_blank">Smart Routing section</a> and <a href="http://docs.hazelcast.org/docs/latest-dev/manual/html-single/index.html##handling-retry-able-operation-failure" target="_blank">Retry-able Operation Failure section</a> for more details.

```xml   
<property name="hibernate.cache.hazelcast.use_native_client">true</property>
```

To set up Native Client, add the Hazelcast **group-name**, **group-password** and **cluster member address** properties. Native Client will connect to the defined member and will get the addresses of all members in the cluster. If the connected member dies or leaves the cluster, the client will automatically switch to another member in the cluster.

```xml  
<property name="hibernate.cache.hazelcast.native_client_address">10.34.22.15</property>
<property name="hibernate.cache.hazelcast.native_client_group">dev</property>
<property name="hibernate.cache.hazelcast.native_client_password">dev-pass</property>
```

You can use an existing client instead of creating a new one by adding the following property.

```xml
<property name="hibernate.cache.hazelcast.native_client_instance_name">my-client</property>
```

***NOTE***: *To configure a Hazelcast Native Client for Hibernate, put the configuration file named `hazelcast-client.xml` into the root of your classpath.*

***NOTE***: *To be able to use native client mode, add `hazelcast-hibernate(5,52,53)` and `hibernate-core` to your remote cluster's classpath.*

***NOTE***: *If your persisted classes only contain Java primitive type fields, you do not need to add your classes into your remote cluster's classpath. However, if your classes have non-primitive type fields, you need to add only these fields' classes (not your domain class) to your cluster's classpath.*

## Configuring Cache Concurrency Strategy

Hazelcast supports three cache concurrency strategies: *read-only*, *read-write*, and *nonstrict-read-write*.

If you are using XML based class configurations, add a *cache* element into your configuration with the *usage* attribute set to one of the *read-only*, *read-write*, or *nonstrict-read-write* strategies.
   
```xml
<class name="eg.Immutable" mutable="false">
  <cache usage="read-only"/>
  .... 
</class>

<class name="eg.Cat" .... >
  <cache usage="read-write"/>
  ....
  <set name="kittens" ... >
    <cache usage="read-write"/>
    ....
  </set>
</class>
```
If you are using Hibernate-Annotations, then you can add a *class-cache* or *collection-cache* element into your Hibernate configuration file with the *usage* attribute set to *read only*, *read/write*, or *nonstrict read/write*.

```xml    
<class-cache usage="read-only" class="eg.Immutable"/>
<class-cache usage="read-write" class="eg.Cat"/>
<collection-cache collection="eg.Cat.kittens" usage="read-write"/>
```

Or alternatively, you can use *@Cache* annotation on your entities and collections.

```java    
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Cat implements Serializable {
  ...
}
```

## Advanced Settings

**Changing/setting lock timeout value of *read-write* strategy in hazelcast-hibernate5 and hazelcast-hibernate52**

You can set a lock timeout value using the `hibernate.cache.hazelcast.lock_timeout` Hibernate property. The value should be in milliseconds.
