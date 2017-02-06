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

package com.hazelcast.hibernate.instance;

import com.hazelcast.hibernate.CacheEnvironment;
import org.hibernate.cache.CacheException;

import java.util.Properties;

/**
 * A factory that returns a {@link com.hazelcast.core.HazelcastInstance} depending on configuration either backed by Hazelcast
 * client or node implementation.
 */
public final class DefaultHazelcastInstanceFactory implements IHazelcastInstanceFactory
{
    private static final String HZ_CLIENT_LOADER_CLASSNAME = "com.hazelcast.hibernate.instance.HazelcastClientLoader";
    private static final String HZ_INSTANCE_LOADER_CLASSNAME = "com.hazelcast.hibernate.instance.HazelcastInstanceLoader";

    public IHazelcastInstanceLoader createInstanceLoader(final Properties props) throws CacheException {
        try {
            Class loaderClass = getInstanceLoaderClass(props);
            IHazelcastInstanceLoader instanceLoader = (IHazelcastInstanceLoader) loaderClass.newInstance();
            instanceLoader.configure(props);
            return instanceLoader;
        } catch (Exception e) {
            throw new CacheException(e);
        }
    }

    private static Class getInstanceLoaderClass(final Properties props) throws ClassNotFoundException {
        ClassLoader cl = DefaultHazelcastInstanceFactory.class.getClassLoader();
        if (props != null && CacheEnvironment.isNativeClient(props)) {
            return cl.loadClass(HZ_CLIENT_LOADER_CLASSNAME);
        } else {
            return cl.loadClass(HZ_INSTANCE_LOADER_CLASSNAME);
        }
    }
}
