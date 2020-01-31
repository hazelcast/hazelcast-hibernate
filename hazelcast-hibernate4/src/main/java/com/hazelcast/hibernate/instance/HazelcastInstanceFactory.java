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

package com.hazelcast.hibernate.instance;

import com.hazelcast.hibernate.CacheEnvironment;
import org.hibernate.cache.CacheException;

import java.util.Properties;

/**
 * A factory class to build up implementations of {@link com.hazelcast.hibernate.instance.IHazelcastInstanceLoader}
 * that returns a {@link com.hazelcast.core.HazelcastInstance} depending on configuration either backed by Hazelcast
 * client or node implementation.
 */
public final class HazelcastInstanceFactory {

    private static final String HZ_CLIENT_LOADER_CLASSNAME = "com.hazelcast.hibernate.instance.HazelcastClientLoader";
    private static final String HZ_INSTANCE_LOADER_CLASSNAME = "com.hazelcast.hibernate.instance.HazelcastInstanceLoader";

    private HazelcastInstanceFactory() {
    }

    public static IHazelcastInstanceLoader createInstanceLoader(Properties props) throws CacheException {
        try {
            IHazelcastInstanceLoader instanceLoader = (IHazelcastInstanceLoader) props.
                    get("com.hazelcast.hibernate.instance.loader");
            if (instanceLoader != null) {
                return instanceLoader;
            }
            Class loaderClass = getInstanceLoaderClass(props);
            instanceLoader = (IHazelcastInstanceLoader) loaderClass.newInstance();
            instanceLoader.configure(props);
            return instanceLoader;
        } catch (Exception e) {
            throw new CacheException(e);
        }
    }

    private static Class getInstanceLoaderClass(Properties props) throws ClassNotFoundException {
        ClassLoader cl = HazelcastInstanceFactory.class.getClassLoader();
        if (props != null && CacheEnvironment.isNativeClient(props)) {
            return cl.loadClass(HZ_CLIENT_LOADER_CLASSNAME);
        } else {
            return cl.loadClass(HZ_INSTANCE_LOADER_CLASSNAME);
        }
    }
}
