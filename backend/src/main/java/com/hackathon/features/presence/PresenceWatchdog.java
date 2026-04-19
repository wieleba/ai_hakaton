package com.hackathon.features.presence;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class PresenceWatchdog {
    private static final long STALE_AFTER_MS = 90_000L;

    private final RedisPresenceService service;
    private final PresencePublisher publisher;

    @Scheduled(fixedRate = 60_000L)
    public void sweep() {
        long cutoff = System.currentTimeMillis() - STALE_AFTER_MS;
        var affected = service.evictStaleOwnedBy(service.getInstanceId(), cutoff);
        for (UUID userId : affected) {
            publisher.publish(userId, service.aggregate(userId));
        }
        if (!affected.isEmpty()) {
            log.debug("Presence watchdog swept {} users with stale sessions", affected.size());
        }
    }
}
