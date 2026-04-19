package com.hackathon.features.presence;

import com.hackathon.shared.websocket.WsSessionMetadataRegistry;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
@Slf4j
public class PresenceEventListener {
    private final PresenceService presenceService;
    private final PresencePublisher presencePublisher;
    private final WsSessionMetadataRegistry metadataRegistry;

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();
        String sessionId = accessor.getSessionId();
        if (user == null || sessionId == null) return;
        UUID userId;
        try {
            userId = UUID.fromString(user.getName());
        } catch (IllegalArgumentException e) {
            log.warn("SessionConnectedEvent with non-UUID principal: {}", user.getName());
            return;
        }
        // SessionConnectedEvent does NOT carry session attributes, so we read UA / IP /
        // tokenHash from the side registry the CONNECT interceptor populated.
        WsSessionMetadataRegistry.Metadata meta = metadataRegistry.get(sessionId);
        String userAgent = meta == null ? null : meta.userAgent();
        String remoteAddr = meta == null ? null : meta.remoteAddr();
        String tokenHash = meta == null ? null : meta.tokenHash();
        presenceService.markOnline(userId, sessionId, userAgent, remoteAddr, tokenHash);
        presencePublisher.publish(userId, presenceService.aggregate(userId));
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();
        String sessionId = accessor.getSessionId();
        if (user == null || sessionId == null) return;
        UUID userId;
        try {
            userId = UUID.fromString(user.getName());
        } catch (IllegalArgumentException e) {
            return;
        }
        presenceService.markOffline(userId, sessionId);
        metadataRegistry.remove(sessionId);
        presencePublisher.publish(userId, presenceService.aggregate(userId));
    }
}
