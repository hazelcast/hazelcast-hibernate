package com.hazelcast.hibernate.telemetry;

import org.hibernate.Hibernate;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class PhoneHomeTest {

    @Test
    public void localRegionQueryStringTest() {
        String queryString = new PhoneHomeInfo(true).getQueryString();
        verifyQueryString(true, queryString);
    }

    @Test
    public void distributedRegionQueryStringTest() {
        String queryString = new PhoneHomeInfo(false).getQueryString();
        verifyQueryString(false, queryString);
    }

    private void verifyQueryString(boolean isLocalRegion, String queryString) {
        Map<String, String> parameters = toParametersMap(queryString);
        String resolvedPluginVersion = PhoneHomeInfo.resolveVersion();

        assertEquals(3, parameters.size());
        assertEquals(isLocalRegion ? "local" : "distributed", parameters.get("region-type"));
        assertEquals(resolvedPluginVersion, parameters.get("version"));
        assertEquals(Hibernate.class.getPackage().getImplementationVersion(), parameters.get("hibernate-version"));
        assertNotEquals("N/A", resolvedPluginVersion);
    }

    private Map<String, String> toParametersMap(String queryString) {
        Map<String, String> parameters = new HashMap<>();
        for (String param: queryString.substring(1).split("&")) { // trim '?' at the beginning of the query
            String[] pair = param.split("=");
            parameters.put(pair[0], pair[1]);
        }
        return parameters;
    }
}
