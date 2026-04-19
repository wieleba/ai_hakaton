package com.hackathon.features.presence;

import static org.mockito.Mockito.*;

import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

class PresenceMessageHandlerTest {
    PresenceService service;
    PresencePublisher publisher;
    PresenceMessageHandler handler;

    @BeforeEach
    void setup() {
        service = mock(PresenceService.class);
        publisher = mock(PresencePublisher.class);
        handler = new PresenceMessageHandler(service, publisher);
    }

    private SimpMessageHeaderAccessor headers(String sessionId) {
        SimpMessageHeaderAccessor h = SimpMessageHeaderAccessor.create();
        h.setSessionId(sessionId);
        return h;
    }

    @Test
    void onAfk_callsMarkAfk_andPublishes() {
        UUID userId = UUID.randomUUID();
        Principal p = () -> userId.toString();
        when(service.aggregate(userId)).thenReturn(PresenceState.AFK);

        handler.onAfk(headers("s1"), p);

        verify(service).markAfk(userId, "s1");
        verify(publisher).publish(userId, PresenceState.AFK);
    }

    @Test
    void onActive_callsMarkActive_andPublishes() {
        UUID userId = UUID.randomUUID();
        Principal p = () -> userId.toString();
        when(service.aggregate(userId)).thenReturn(PresenceState.ONLINE);

        handler.onActive(headers("s1"), p);

        verify(service).markActive(userId, "s1");
        verify(publisher).publish(userId, PresenceState.ONLINE);
    }

    @Test
    void onHeartbeat_callsHeartbeat_andDoesNotPublish() {
        UUID userId = UUID.randomUUID();
        Principal p = () -> userId.toString();

        handler.onHeartbeat(headers("s1"), p);

        verify(service).heartbeat(userId, "s1");
        verifyNoInteractions(publisher);
    }

    @Test
    void onAfk_withoutPrincipal_isNoOp() {
        handler.onAfk(headers("s1"), null);
        verifyNoInteractions(service);
        verifyNoInteractions(publisher);
    }
}
