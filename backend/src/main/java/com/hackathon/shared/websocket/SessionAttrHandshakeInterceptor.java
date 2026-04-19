package com.hackathon.shared.websocket;

import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Copies the HTTP User-Agent and remote-address from the initial SockJS handshake
 * into the WS session attributes. STOMP CONNECT frames don't carry the IP and
 * SockJS doesn't surface the User-Agent as a STOMP header, so we grab both here.
 */
@Component
public class SessionAttrHandshakeInterceptor implements HandshakeInterceptor {
  public static final String ATTR_USER_AGENT = "userAgent";
  public static final String ATTR_REMOTE_ADDR = "remoteAddr";

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {
    // ConcurrentHashMap (used downstream by Spring's SockJS session map) rejects null
    // values — only put these when we actually have a value. PresenceEventListener
    // already treats missing keys as null.
    var agents = request.getHeaders().get("User-Agent");
    if (agents != null && !agents.isEmpty() && agents.get(0) != null) {
      attributes.put(ATTR_USER_AGENT, agents.get(0));
    }

    if (request instanceof ServletServerHttpRequest servletReq) {
      String remoteAddr = servletReq.getServletRequest().getRemoteAddr();
      if (remoteAddr != null) attributes.put(ATTR_REMOTE_ADDR, remoteAddr);
    }
    return true;
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {}
}
