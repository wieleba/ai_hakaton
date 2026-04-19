package com.hackathon.features.sessions;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * Forces a STOMP session closed by pushing a DISCONNECT frame onto the outbound
 * channel. Safe to call with a session id that no longer exists — Spring treats
 * it as a no-op.
 */
@Component
public class SessionDisconnector {
  private final MessageChannel clientOutboundChannel;

  public SessionDisconnector(
      @Qualifier("clientOutboundChannel") MessageChannel clientOutboundChannel) {
    this.clientOutboundChannel = clientOutboundChannel;
  }

  public void disconnect(String sessionId) {
    StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.DISCONNECT);
    acc.setSessionId(sessionId);
    acc.setLeaveMutable(true);
    clientOutboundChannel.send(
        MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders()));
  }
}
