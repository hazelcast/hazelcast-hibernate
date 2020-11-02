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

package com.hazelcast.hibernate.telemetry;

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
public class PhoneHomeService {

    private static final String SYS_PHONE_HOME_ENABLED = "hazelcast.phone.home.enabled";
    private static final String ENV_PHONE_HOME_ENABLED = "HZ_PHONE_HOME_ENABLED";

    private static final String BASE_PHONE_HOME_URL = "http://phonehome.hazelcast.com/pingIntegrations";
    private static final String QUERY_STRING = PhoneHomeInfo.getQueryString();
    private static final Duration TIMEOUT = Duration.ofMillis(3000);

    private final ILogger logger = Logger.getLogger(PhoneHomeService.class);
    private final AtomicBoolean started = new AtomicBoolean();
    private final ScheduledExecutorService executor = newSingleThreadScheduledExecutor(r
            -> new Thread(r, "PhoneHomeService"));

    public PhoneHomeService() {
    }

    public void start() {
        if (started.compareAndSet(false, true) && isPhoneHomeEnabled()) {
            executor.scheduleAtFixedRate(this::send, 0, 1, TimeUnit.DAYS);
        }
    }

    public static boolean isPhoneHomeEnabled() {
        String falseStr = FALSE.toString();
        if (falseStr.equalsIgnoreCase(getenv(ENV_PHONE_HOME_ENABLED))) {
            return false;
        }
        if (falseStr.equalsIgnoreCase(System.getProperty(SYS_PHONE_HOME_ENABLED))) {
            return false;
        }
        return true;
    }

    private void send() {
        // If a value changing over time is sent to the server,
        // QUERY_STRING - which contains the call data, must
        // be updated before each call.
        InputStream in = null;
        try {
            URL url = new URL(BASE_PHONE_HOME_URL + QUERY_STRING);
            URLConnection conn = url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout((int) TIMEOUT.toMillis());
            conn.setReadTimeout((int) TIMEOUT.toMillis());
            in = new BufferedInputStream(conn.getInputStream());
        } catch (Exception e) {
            if (logger.isFineEnabled()) {
                logger.fine("Failed to establish home phone call.", e);
            }
        } finally {
            closeResource(in);
        }
    }

    public void shutdown() {
        executor.shutdown();
    }

}
