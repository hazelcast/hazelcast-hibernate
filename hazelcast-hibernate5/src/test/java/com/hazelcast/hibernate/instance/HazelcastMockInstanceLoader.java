package com.hazelcast.hibernate.instance;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientNetworkConfig;
import com.hazelcast.client.config.ConnectionRetryConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.client.test.TestHazelcastFactory;
import com.hazelcast.config.Config;
import com.hazelcast.internal.config.ConfigLoader;
import com.hazelcast.config.InvalidConfigurationException;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.hibernate.CacheEnvironment;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import org.hibernate.cache.CacheException;
import org.hibernate.internal.util.config.ConfigurationHelper;

import java.io.IOException;
import java.util.Properties;

public class HazelcastMockInstanceLoader implements IHazelcastInstanceLoader {

    private static final ILogger LOGGER = Logger.getLogger(HazelcastMockInstanceLoader.class);
    private static final int INITIAL_BACKOFF_MS = 2000;
    private static final int MAX_BACKOFF_MS = 35000;
    private static final double BACKOFF_MULTIPLIER = 1.5D;

    private final Properties props = new Properties();
    private String instanceName;
    private HazelcastInstance instance;
    private Config config;
    private HazelcastInstance client;
    private TestHazelcastFactory factory;
    public void configure(Properties props) {
        this.props.putAll(props);
    }

    public HazelcastInstance loadInstance() throws CacheException {
        if (CacheEnvironment.isNativeClient(props)) {
            if (client != null && client.getLifecycleService().isRunning()) {
                LOGGER.warning("Current HazelcastClient is already active! Shutting it down...");
                unloadInstance();
            }
            String address = ConfigurationHelper.getString(CacheEnvironment.NATIVE_CLIENT_ADDRESS, props, null);
            String clientClusterName = ConfigurationHelper.getString(CacheEnvironment.NATIVE_CLIENT_CLUSTER_NAME, props, null);
            String configResourcePath = CacheEnvironment.getConfigFilePath(props);

            ClientConfig clientConfig = buildClientConfig(configResourcePath);
            if (clientClusterName != null) {
                clientConfig.setClusterName(clientClusterName);
            }
            if (address != null) {
                clientConfig.getNetworkConfig().addAddress(address);
            }
            clientConfig.getNetworkConfig().setSmartRouting(true);
            clientConfig.getNetworkConfig().setRedoOperation(true);
            client = factory.newHazelcastClient(clientConfig);
            return client;
        } else {
            if (instance != null && instance.getLifecycleService().isRunning()) {
                LOGGER.warning("Current HazelcastInstance is already loaded and running! "
                        + "Returning current instance...");
                return instance;
            }
            String configResourcePath = null;
            instanceName = CacheEnvironment.getInstanceName(props);
            configResourcePath = CacheEnvironment.getConfigFilePath(props);
            if (!isEmpty(configResourcePath)) {
                try {
                    config = ConfigLoader.load(configResourcePath);
                } catch (IOException e) {
                    LOGGER.warning("IOException: " + e.getMessage());
                }
                if (config == null) {
                    throw new CacheException("Could not find configuration file: "
                            + configResourcePath);
                }
            }
            if (instanceName != null) {
                instance = getHazelcastInstanceByName(instanceName);
                if (instance == null) {
                    try {
                        createOrGetInstance();
                    } catch (InvalidConfigurationException ignored) {
                        instance = getHazelcastInstanceByName(instanceName);
                    }
                }
            } else {
                createOrGetInstance();
            }
            return instance;
        }
    }

    private void createOrGetInstance() throws InvalidConfigurationException {
        if (config == null) {
            config = new XmlConfigBuilder().build();
        }
        config.setInstanceName(instanceName);
        instance = factory.newHazelcastInstance(config);
    }

    public void unloadInstance() throws CacheException {
        if (CacheEnvironment.isNativeClient(props)) {
            if (client == null) {
                return;
            }
            try {
                client.getLifecycleService().shutdown();
                client = null;
            } catch (Exception e) {
                throw new CacheException(e);
            }
        } else {
            if (instance == null) {
                return;
            }
            final boolean shutDown = CacheEnvironment.shutdownOnStop(props, (instanceName == null));
            if (!shutDown) {
                LOGGER.warning(CacheEnvironment.SHUTDOWN_ON_STOP + " property is set to 'false'. "
                        + "Leaving current HazelcastInstance active! (Warning: Do not disable Hazelcast "
                        + CacheEnvironment.HAZELCAST_SHUTDOWN_HOOK_ENABLED + " property!)");
                return;
            }
            try {
                instance.getLifecycleService().shutdown();
                instance = null;
                factory.shutdownAll();
            } catch (Exception e) {
                throw new CacheException(e);
            }
        }
    }

    public TestHazelcastFactory getInstanceFactory() throws CacheException {
        return factory;
    }

    public void setInstanceFactory(TestHazelcastFactory factory) {
        this.factory = factory;
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().length() == 0;
    }

    private HazelcastInstance getHazelcastInstanceByName(String instanceName) {
        HazelcastInstance foundInstance = null;
        for (HazelcastInstance instance : factory.getAllHazelcastInstances()) {
            if(instanceName.equals(instance.getName())) {
                foundInstance = instance;
            }
        }
        return foundInstance;
    }

    private ClientConfig buildClientConfig(String configResourcePath) {
        ClientConfig clientConfig = null;
        if (configResourcePath != null) {
            try {
                clientConfig = new XmlClientConfigBuilder(configResourcePath).build();
            } catch (IOException e) {
                LOGGER.warning("Could not load client configuration: " + configResourcePath, e);
            }
        }
        if (clientConfig == null) {
            clientConfig = new ClientConfig();
            final ClientNetworkConfig networkConfig = clientConfig.getNetworkConfig();
            final ConnectionRetryConfig connectionRetryConfig = clientConfig.getConnectionStrategyConfig().getConnectionRetryConfig();
            networkConfig.setSmartRouting(true);
            networkConfig.setRedoOperation(true);
            connectionRetryConfig.setInitialBackoffMillis(INITIAL_BACKOFF_MS);
            connectionRetryConfig.setMaxBackoffMillis(MAX_BACKOFF_MS);
            connectionRetryConfig.setMultiplier(BACKOFF_MULTIPLIER);
        }
        return clientConfig;
    }
}
