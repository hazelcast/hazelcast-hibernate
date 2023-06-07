package com.hazelcast.hibernate;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static org.awaitility.Awaitility.await;

public class PhoneHomeIntegrationTest {

    private final WireMockServer wireMockServer = new WireMockServer();

    @Before
    public void setup() {
        System.setProperty("hazelcast.phone.home.enabled", "true");
        wireMockServer.start();
    }

    @Test
    public void phoneHomeRequestTest() {
        PhoneHomeInfo info = new PhoneHomeInfo(true);
        PhoneHomeService service = new PhoneHomeService("http://127.0.0.1:8080" +
                "/hazelcast-hibernate53", info);
        String url = "/hazelcast-hibernate53" + info.getQueryString();

        stubFor(get(urlEqualTo(url)).willReturn(aResponse().withStatus(HTTP_NOT_FOUND)));

        service.start();

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                // verify 5 retries
                WireMock.verify(5, getRequestedFor(urlEqualTo(url)))
        );

        service.shutdown();
    }

    @After
    public void teardown() {
        System.setProperty("hazelcast.phone.home.enabled", "false");
        wireMockServer.stop();
    }

}
