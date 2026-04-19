package com.hackathon.features.presence;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class PresenceSubscriber implements MessageListener {
  public static final String CLIENT_TOPIC = "/topic/presence";

  private final RedisMessageListenerContainer container;
  private final SimpMessagingTemplate messagingTemplate;
  private final ObjectMapper objectMapper;

  @PostConstruct
  void subscribe() {
    Topic topic = new ChannelTopic(RedisPresencePublisher.CHANNEL);
    container.addMessageListener(this, topic);
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    try {
      String body = new String(message.getBody());
      var payload = objectMapper.readValue(body, RedisPresencePublisher.PresencePayload.class);
      messagingTemplate.convertAndSend(CLIENT_TOPIC, Map.of(
          "userId", UUID.fromString(payload.userId()),
          "state", payload.state()));
    } catch (Exception e) {
      log.warn("Failed to forward presence event: {}", e.getMessage());
    }
  }
}
