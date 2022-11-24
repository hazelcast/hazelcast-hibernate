package com.hazelcast.hibernate.instance;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.hibernate.CacheEnvironment;
import com.hazelcast.hibernate.HibernateTestSupport;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.Properties;

public class HazelcastClientLoaderTest extends HibernateTestSupport {

    @Test
    public void configureMultipleAddresses() {
        HazelcastClientLoader loader = new HazelcastClientLoader();
        Properties props = new Properties();
        props.setProperty(CacheEnvironment.NATIVE_CLIENT_ADDRESS, " localhost, 127.0.0.1 ");
        loader.configure(props);
        ClientConfig clientConfig = loader.getClientConfig();
        Assertions.assertThat(clientConfig.getNetworkConfig().getAddresses()).containsExactly("localhost", "127.0.0.1");
    }

    @Test
    public void configureSingleAddress() {
        HazelcastClientLoader loader = new HazelcastClientLoader();
        Properties props = new Properties();
        props.setProperty(CacheEnvironment.NATIVE_CLIENT_ADDRESS, "localhost");
        loader.configure(props);
        ClientConfig clientConfig = loader.getClientConfig();
        Assertions.assertThat(clientConfig.getNetworkConfig().getAddresses()).containsExactly("localhost");
    }

}
