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

import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.hazelcast.internal.nio.IOUtil.closeResource;
import static java.lang.Boolean.FALSE;
import static java.lang.System.getenv;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

/**
 * Pings phone home server with plugin information daily.
 *
 * @since 2.1.2
 */
class PhoneHomeService {

    private static final String SYS_PHONE_HOME_ENABLED = "hazelcast.phone.home.enabled";
    private static final String ENV_PHONE_HOME_ENABLED = "HZ_PHONE_HOME_ENABLED";

    private static final String PHONE_HOME_URL = "http://phonehome.hazelcast.com/pingIntegrations/hazelcast-hibernate5";
    private static final Duration TIMEOUT = Duration.ofMillis(3000);
    private static final int RETRY_COUNT = 5;
    private static final boolean PHONE_HOME_ENABLED = isPhoneHomeEnabled();
    private static ScheduledExecutorService executor;

    private final ILogger logger = Logger.getLogger(PhoneHomeService.class);
    private final AtomicBoolean started = new AtomicBoolean();

    private final PhoneHomeInfo phoneHomeInfo;

    static {
        if (PHONE_HOME_ENABLED) {
            executor = newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Hazelcast-Hibernate.PhoneHomeService");
                t.setDaemon(true);
                return t;
            });
        }
    }

    PhoneHomeService(PhoneHomeInfo phoneHomeInfo) {
        this.phoneHomeInfo = phoneHomeInfo;
    }

    private static boolean isPhoneHomeEnabled() {
        if (FALSE == Boolean.parseBoolean(System.getProperty(SYS_PHONE_HOME_ENABLED))) {
            return false;
        }
        if (FALSE == Boolean.parseBoolean(getenv(ENV_PHONE_HOME_ENABLED))) {
            return false;
        }
        return true;
    }

    void start() {
        if (started.compareAndSet(false, true) && PHONE_HOME_ENABLED) {
            executor.scheduleAtFixedRate(this::send, 0, 1, TimeUnit.DAYS);
        }
    }

    private void send() {
        int retryCount = RETRY_COUNT;
        boolean succeed = false;
        while (retryCount-- > 0 && !succeed) {
            InputStream in = null;
            try {
                URL url = new URL(PHONE_HOME_URL + phoneHomeInfo.getQueryString());
                URLConnection conn = url.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setConnectTimeout((int) TIMEOUT.toMillis());
                conn.setReadTimeout((int) TIMEOUT.toMillis());
                in = new BufferedInputStream(conn.getInputStream());
                succeed = true;
            } catch (Exception e) {
                if (logger.isFineEnabled()) {
                    logger.fine("Failed to establish home phone call. Retries left: " + retryCount, e);
                }
            } finally {
                closeResource(in);
            }
        }
    }

    void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

}
