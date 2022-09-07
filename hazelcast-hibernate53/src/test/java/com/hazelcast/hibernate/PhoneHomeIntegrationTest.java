package com.hazelcast.hibernate;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

public class PhoneHomeIntegrationTest {

    private WireMockServer wireMockServer = new WireMockServer();

    @Before
    public void setup() {
        System.setProperty("hazelcast.phone.home.enabled", "true");
        wireMockServer.start();
    }

    @Test
    public void phoneHomeRequestTest() {
        PhoneHomeInfo info = new PhoneHomeInfo(true);
        PhoneHomeService service = new PhoneHomeService("http://127.0.0.1:" + wireMockServer.port() +
                "/hazelcast-hibernate53", info);
        service.start();

        // verify 5 retries
        WireMock.verify(5, getRequestedFor(urlEqualTo("/hazelcast-hibernate53" + info.getQueryString())));

        service.shutdown();
    }

    @After
    public void teardown() {
        System.setProperty("hazelcast.phone.home.enabled", "false");
        wireMockServer.stop();
    }

}
