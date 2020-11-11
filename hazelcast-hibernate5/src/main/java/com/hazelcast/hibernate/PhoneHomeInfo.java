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
import org.hibernate.Hibernate;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Creates query string according to plugin properties to be sent to phone home
 * server by {@link PhoneHomeService}.
 *
 * Since no dynamic information is sent by phone home calls, the query string is
 * built once in the constructor. In case of the addition of a new field to home
 * calls which requires query string to be updated before each call, this class
 * needs to be changed such that it returns appropriate query string with up to
 * date information rather than a static one.
 *
 * @since 2.1.2
 */
class PhoneHomeInfo {

    private static final String PROPERTIES_RESOURCE = "/phone.home.properties";

    private String version;
    private String queryString;

    PhoneHomeInfo(boolean isLocalRegion) {
        this.version = resolveVersion();
        this.queryString = buildQueryString(isLocalRegion);
    }

    String getQueryString() {
        return queryString;
    }

    static String resolveVersion() {
        // To resolve the version described in the pom file, filtering for
        // phone.home.properties must be enabled in the resources section
        // of the pom.xml
        Properties properties = new Properties();
        try (InputStream propertiesStream = PhoneHomeInfo.class.getResourceAsStream(PROPERTIES_RESOURCE)) {
            properties.load(propertiesStream);
            return properties.getProperty("project.version", "N/A");
        } catch (IOException ignored) {
            return "N/A";
        }
    }

    private String buildQueryString(boolean isLocalRegion) {
        // Any change committed here must correspond to the phone
        // home server changes. Do not make standalone changes
        // especially for the parameter keys.
        return new QueryStringBuilder()
                .addParam("version", version)
                .addParam("hibernate-version", Hibernate.class.getPackage().getImplementationVersion())
                .addParam("region-type", isLocalRegion ? "local" : "distributed")
                .build();
    }

    /**
     * Constructs query string using the added parameters.
     */
    private static class QueryStringBuilder {

        private final ILogger logger = Logger.getLogger(QueryStringBuilder.class);
        private final StringBuilder builder;

        QueryStringBuilder() {
            builder = new StringBuilder("?");
        }

        QueryStringBuilder addParam(String key, String value) {
            if (builder.length() > 1) {
                builder.append("&");
            }
            builder.append(key).append("=").append(tryEncode(value));
            return this;
        }

        private String tryEncode(String value) {
            try {
                return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
            } catch (UnsupportedEncodingException e) {
                if (logger.isFineEnabled()) {
                    logger.fine("Using <unknown> for the value which couldn't be encoded: " + value, e);
                }
                // return the known encoding of the word `unknown` which is unchanged.
                return "unknown";
            }
        }

        String build() {
            return builder.toString();
        }
    }

}
