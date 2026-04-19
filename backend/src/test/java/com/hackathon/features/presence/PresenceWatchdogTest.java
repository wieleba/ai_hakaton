package com.hackathon.features.presence;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PresenceWatchdogTest {
    @Autowired InMemoryPresenceService service;

    @Test
    void evictStale_removesOwnedSession() throws InterruptedException {
        UUID u = UUID.randomUUID();
        service.markOnline(u, "s1");
        Thread.sleep(5);
        long cutoff = System.currentTimeMillis();
        service.evictStaleOwnedBy(service.getInstanceId(), cutoff);
        assertEquals(PresenceState.OFFLINE, service.aggregate(u));
    }

    @Test
    void evictStale_leavesForeignSessionsAlone() throws InterruptedException {
        UUID u = UUID.randomUUID();
        service.markOnline(u, "s1");
        Thread.sleep(5);
        long cutoff = System.currentTimeMillis();
        service.evictStaleOwnedBy("some-other-instance", cutoff);
        assertEquals(PresenceState.ONLINE, service.aggregate(u));
    }
}
