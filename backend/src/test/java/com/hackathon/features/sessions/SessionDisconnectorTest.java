package com.hackathon.features.sessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

class SessionDisconnectorTest {

  @Test
  void disconnectSendsStompDisconnectForSessionId() {
    MessageChannel channel = mock(MessageChannel.class);
    when(channel.send(any())).thenReturn(true);
    SessionDisconnector disconnector = new SessionDisconnector(channel);

    disconnector.disconnect("s-abc");

    ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
    verify(channel).send(captor.capture());
    StompHeaderAccessor acc = StompHeaderAccessor.wrap(captor.getValue());
    assertThat(acc.getCommand()).isEqualTo(StompCommand.DISCONNECT);
    assertThat(acc.getSessionId()).isEqualTo("s-abc");
  }
}
