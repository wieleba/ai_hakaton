package com.hackathon.features.presence;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

class PresenceEventListenerTest {
    PresenceService service;
    PresencePublisher publisher;
    PresenceEventListener listener;

    @BeforeEach
    void setup() {
        service = mock(PresenceService.class);
        publisher = mock(PresencePublisher.class);
        listener = new PresenceEventListener(service, publisher);
    }

    private Message<byte[]> stompMessage(String sessionId, Principal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECTED);
        accessor.setSessionId(sessionId);
        accessor.setUser(principal);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void onConnect_marksOnline_andPublishes() {
        UUID userId = UUID.randomUUID();
        Principal p = () -> userId.toString();
        SessionConnectedEvent event = new SessionConnectedEvent(this, stompMessage("s1", p));

        when(service.aggregate(userId)).thenReturn(PresenceState.ONLINE);

        listener.onConnect(event);

        verify(service).markOnline(userId, "s1");
        verify(publisher).publish(userId, PresenceState.ONLINE);
    }

    @Test
    void onDisconnect_marksOffline_andPublishes() {
        UUID userId = UUID.randomUUID();
        Principal p = () -> userId.toString();
        SessionDisconnectEvent event = new SessionDisconnectEvent(
                this, stompMessage("s1", p), "s1", CloseStatus.NORMAL);

        when(service.aggregate(userId)).thenReturn(PresenceState.OFFLINE);

        listener.onDisconnect(event);

        verify(service).markOffline(userId, "s1");
        verify(publisher).publish(userId, PresenceState.OFFLINE);
    }

    @Test
    void onConnect_withoutPrincipal_isNoOp() {
        Message<byte[]> msg = stompMessage("s1", null);
        SessionConnectedEvent event = new SessionConnectedEvent(this, msg);

        listener.onConnect(event);

        verifyNoInteractions(service);
        verifyNoInteractions(publisher);
    }

    @Test
    void onConnect_withNonUuidPrincipal_isNoOp() {
        Principal p = () -> "not-a-uuid";
        SessionConnectedEvent event = new SessionConnectedEvent(this, stompMessage("s1", p));

        listener.onConnect(event);

        verifyNoInteractions(service);
        verifyNoInteractions(publisher);
    }
}
