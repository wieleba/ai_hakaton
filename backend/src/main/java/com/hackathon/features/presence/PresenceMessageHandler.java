package com.hackathon.features.presence;

import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class PresenceMessageHandler {
    private final PresenceService service;
    private final PresencePublisher publisher;

    @MessageMapping("/presence/afk")
    public void onAfk(SimpMessageHeaderAccessor headers, Principal principal) {
        handle(principal, headers.getSessionId(), Transition.AFK);
    }

    @MessageMapping("/presence/active")
    public void onActive(SimpMessageHeaderAccessor headers, Principal principal) {
        handle(principal, headers.getSessionId(), Transition.ACTIVE);
    }

    @MessageMapping("/presence/heartbeat")
    public void onHeartbeat(SimpMessageHeaderAccessor headers, Principal principal) {
        handle(principal, headers.getSessionId(), Transition.HEARTBEAT);
    }

    private enum Transition { AFK, ACTIVE, HEARTBEAT }

    private void handle(Principal principal, String sessionId, Transition t) {
        if (principal == null || sessionId == null) return;
        UUID userId;
        try {
            userId = UUID.fromString(principal.getName());
        } catch (IllegalArgumentException e) {
            return;
        }
        switch (t) {
            case AFK -> service.markAfk(userId, sessionId);
            case ACTIVE -> service.markActive(userId, sessionId);
            case HEARTBEAT -> service.heartbeat(userId, sessionId);
        }
        // Heartbeats don't change aggregation state; only AFK/ACTIVE transitions do.
        if (t != Transition.HEARTBEAT) {
            publisher.publish(userId, service.aggregate(userId));
        }
    }
}
