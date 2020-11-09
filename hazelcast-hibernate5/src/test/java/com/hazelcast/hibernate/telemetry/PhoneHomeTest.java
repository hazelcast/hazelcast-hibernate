package com.hazelcast.hibernate.telemetry;

import com.hazelcast.hibernate.HazelcastCacheRegionFactory;
import com.hazelcast.hibernate.HazelcastLocalCacheRegionFactory;
import org.hibernate.Hibernate;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class PhoneHomeTest {

    @Mock
    PhoneHomeService phoneHomeService;

    @Mock
    SessionFactoryOptions sessionFactoryOptions;

    @Test
    public void localRegionRegistryTest() {
        HazelcastLocalCacheRegionFactory regionFactory = new HazelcastLocalCacheRegionFactory();
        regionFactory.setPhoneHomeService(phoneHomeService);
        regionFactory.start(sessionFactoryOptions, new Properties());
        verify(phoneHomeService, times(1)).start();
    }

    @Test
    public void distributedRegionRegistryTest() {
        HazelcastCacheRegionFactory regionFactory = new HazelcastCacheRegionFactory();
        regionFactory.setPhoneHomeService(phoneHomeService);
        regionFactory.start(sessionFactoryOptions, new Properties());
        verify(phoneHomeService, times(1)).start();
    }

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
