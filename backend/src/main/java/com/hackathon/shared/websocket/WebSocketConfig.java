package com.hackathon.shared.websocket;

import com.hackathon.shared.security.JwtTokenProvider;
import com.hackathon.shared.security.TokenHashing;
import com.hackathon.shared.security.TokenRevocationService;
import java.security.Principal;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
  private final JwtTokenProvider jwtTokenProvider;
  private final SessionAttrHandshakeInterceptor handshakeInterceptor;
  private final TokenRevocationService tokenRevocationService;
  private final WsSessionMetadataRegistry metadataRegistry;

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    config.enableSimpleBroker("/topic", "/queue", "/user");
    config.setApplicationDestinationPrefixes("/app");
    config.setUserDestinationPrefix("/user");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry
        .addEndpoint("/ws/chat")
        .setAllowedOriginPatterns("*")
        .addInterceptors(handshakeInterceptor)
        .withSockJS();
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(
        new ChannelInterceptor() {
          @Override
          public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
              String authHeader = accessor.getFirstNativeHeader("Authorization");
              if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                String hash = TokenHashing.sha256Hex(token);
                if (!jwtTokenProvider.validateToken(token)
                    || tokenRevocationService.isRevoked(hash)) {
                  throw new MessageDeliveryException("Unauthorized CONNECT");
                }
                var userId = jwtTokenProvider.getUserIdFromToken(token);
                Principal principal =
                    new UsernamePasswordAuthenticationToken(
                        userId.toString(), null, Collections.emptyList());
                accessor.setUser(principal);

                // Spring doesn't propagate session attributes onto SessionConnectedEvent,
                // so stash UA / IP (from handshake attrs) plus tokenHash in a side
                // registry keyed by STOMP session id for PresenceEventListener to read.
                String sessionId = accessor.getSessionId();
                var attrs = accessor.getSessionAttributes();
                String ua = null, ip = null;
                if (attrs != null) {
                  Object uaObj = attrs.get(SessionAttrHandshakeInterceptor.ATTR_USER_AGENT);
                  Object ipObj = attrs.get(SessionAttrHandshakeInterceptor.ATTR_REMOTE_ADDR);
                  ua = uaObj == null ? null : uaObj.toString();
                  ip = ipObj == null ? null : ipObj.toString();
                }
                metadataRegistry.put(
                    sessionId, new WsSessionMetadataRegistry.Metadata(ua, ip, hash));
              }
            }
            return message;
          }
        });
  }
}
