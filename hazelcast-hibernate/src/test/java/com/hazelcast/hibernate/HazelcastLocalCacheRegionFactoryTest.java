package com.hazelcast.hibernate;

import org.junit.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

public class HazelcastLocalCacheRegionFactoryTest {

    @Test
    public void softLockExpiresAfterSixtySeconds() {
        HazelcastLocalCacheRegionFactory factory = new HazelcastLocalCacheRegionFactory();

        // AbstractReadWriteAccess.lockItem() sets a soft lock to expire at nextTimestamp() + getTimeout()
        long expiresIn = factory.nextTimestamp() + factory.getTimeout() - System.currentTimeMillis();

        assertThat(expiresIn).isCloseTo(Duration.ofSeconds(60).toMillis(), within(1_000L));
    }
}
