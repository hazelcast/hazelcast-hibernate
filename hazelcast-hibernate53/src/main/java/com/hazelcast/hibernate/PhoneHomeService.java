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
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Boolean.FALSE;
import static java.lang.System.getenv;

/**
 * Pings phone home server with plugin information daily.
 *
 * @since 2.1.2
 */
class PhoneHomeService {

    private static final String SYS_PHONE_HOME_ENABLED = "hazelcast.phone.home.enabled";
    private static final String ENV_PHONE_HOME_ENABLED = "HZ_PHONE_HOME_ENABLED";

    private static final Duration TIMEOUT = Duration.ofMillis(3000);
    private static final int RETRY_COUNT = 5;
    private static ScheduledThreadPoolExecutor executor;

    private final ILogger logger = Logger.getLogger(PhoneHomeService.class);
    private final AtomicBoolean started = new AtomicBoolean();

    private final String baseUrl;
    private final PhoneHomeInfo phoneHomeInfo;
    private ScheduledFuture<?> sendFuture;

    static {
            executor = new ScheduledThreadPoolExecutor(0, r -> {
                Thread t = new Thread(r, "Hazelcast-Hibernate.PhoneHomeService");
                t.setDaemon(true);
                return t;
            });
            executor.setRemoveOnCancelPolicy(true);
        }

    PhoneHomeService(PhoneHomeInfo phoneHomeInfo) {
        this("http://phonehome.hazelcast.com/pingIntegrations/hazelcast-hibernate53", phoneHomeInfo);
    }

    PhoneHomeService(String baseUrl, PhoneHomeInfo phoneHomeInfo) {
        this.baseUrl = baseUrl;
        this.phoneHomeInfo = phoneHomeInfo;
    }

    private static boolean isPhoneHomeEnabled() {
        if (FALSE.toString().equalsIgnoreCase(System.getProperty(SYS_PHONE_HOME_ENABLED))) {
            return false;
        }
        if (FALSE.toString().equalsIgnoreCase(getenv(ENV_PHONE_HOME_ENABLED))) {
            return false;
        }
        return true;
    }

    void start() {
        if (isPhoneHomeEnabled() && started.compareAndSet(false, true)) {
            sendFuture = executor.scheduleAtFixedRate(this::send, 0, 1, TimeUnit.DAYS);
        }
    }

    private void send() {
        int retryCount = RETRY_COUNT;
        boolean succeed = false;
        while (retryCount-- > 0 && !succeed) {
            try (InputStream ignored = new BufferedInputStream(getUrlConnection().getInputStream())) {
                succeed = true;
            } catch (Exception e) {
                if (logger.isFineEnabled()) {
                    logger.fine("Failed to establish home phone call. Retries left: " + retryCount, e);
                }
            }
        }
    }

    private URLConnection getUrlConnection() throws IOException {
        URL url = new URL(baseUrl + phoneHomeInfo.getQueryString());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setConnectTimeout((int) TIMEOUT.toMillis());
        conn.setReadTimeout((int) TIMEOUT.toMillis());
        return conn;
    }

    void shutdown() {
        // Do not shutdown the executor directly. Instead, cancel the job
        // as there might be other tasks scheduled by other factories.
        // If no other task remains, pool size will be zero.
        if (sendFuture != null) {
            sendFuture.cancel(false);
        }
    }

}
