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

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.hibernate.entity.AnnotatedEntity;
import com.hazelcast.hibernate.instance.HazelcastAccessor;
import com.hazelcast.hibernate.instance.IHazelcastInstanceLoader;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.nio.IOUtil;
import com.hazelcast.test.HazelcastTestSupport;
import org.hibernate.SessionFactory;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cfg.Configuration;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.*;
import java.util.Properties;

public abstract class HibernateTestSupport extends HazelcastTestSupport {

    private final ILogger logger = Logger.getLogger(getClass());

    @BeforeClass
    @AfterClass
    public static void tearUpAndDown() {
        Hazelcast.shutdownAll();
    }

    @After
    public final void cleanup() {
        Hazelcast.shutdownAll();
    }

    protected String getCacheStrategy() {
        return  AccessType.READ_WRITE.getExternalName();
    }

    protected void sleep(int seconds) {
        try {
            Thread.sleep(1000 * seconds);
        } catch (InterruptedException e) {
            logger.warning("", e);
        }
    }

    protected void addHbmMappings(final Configuration conf) {
        conf.addFile(createHbmXml("DummyEntity"));
        conf.addFile(createHbmXml("DummyProperty"));
    }

    protected void addMappings(final Configuration conf) {
        conf.addAnnotatedClass(AnnotatedEntity.class);
        addHbmMappings(conf);
    }

    protected SessionFactory createSessionFactory(final Properties props,
                                                  final IHazelcastInstanceLoader customInstanceLoader) {
        props.put(CacheEnvironment.EXPLICIT_VERSION_CHECK, "true");
        if (customInstanceLoader != null) {
            props.put("com.hazelcast.hibernate.instance.loader", customInstanceLoader);
            customInstanceLoader.configure(props);
        } else {
            props.remove("com.hazelcast.hibernate.instance.loader");
        }

        final Configuration conf = new Configuration();
        conf.configure(HibernateTestSupport.class.getClassLoader().getResource("test-hibernate.cfg.xml"));
        addMappings(conf);
        conf.addProperties(props);

        final SessionFactory sf = conf.buildSessionFactory();
        sf.getStatistics().setStatisticsEnabled(true);

        return sf;
    }

    protected HazelcastInstance getHazelcastInstance(final SessionFactory sf) {
        return HazelcastAccessor.getHazelcastInstance(sf);
    }

    /**
     * Starting in Hibernate 5, it is no longer possible to set an explicit cache mode for an entity on the
     * {@code Configuration} object. The cache mode can <i>only</i> be defined in the {@code hbm.xml} file,
     * for entities configured via XML, or directly on the annotated class.
     * <p>
     * To work around that restriction, the {@code hbm.xml} files are treated as templates, with placeholders
     * for their cache modes. This method opens that template resource, replaces the cache mode and writes it
     * to a temporary file (which is marked for delete-on-exit to do housekeeping). This way, the test being
     * run can still control the caching mode.
     *
     * @param baseName the base name for the {@code hbm.xml} file to create
     */
    private File createHbmXml(final String baseName) {
        InputStream inputStream = null;
        BufferedReader reader = null;
        BufferedWriter writer = null;

        try {
            inputStream = getClass().getResourceAsStream("/hbm/" + baseName + ".xml");
            reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));

            final File hbmXmlFile = File.createTempFile(baseName, "hbm.xml");
            hbmXmlFile.deleteOnExit();

            writer = new BufferedWriter(new FileWriter(hbmXmlFile));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                writer.write(String.format(line, getCacheStrategy()));
                writer.newLine();
            }
            writer.flush();

            return hbmXmlFile;
        } catch (final IOException e) {
            throw new IllegalStateException("Could not prepare " + baseName + ".hbm.xml", e);
        } finally {
            IOUtil.closeResource(writer);
            IOUtil.closeResource(reader);
            IOUtil.closeResource(inputStream);
        }
    }
}
