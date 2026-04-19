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
    String userAgent = null;
    var agents = request.getHeaders().get("User-Agent");
    if (agents != null && !agents.isEmpty()) userAgent = agents.get(0);
    attributes.put(ATTR_USER_AGENT, userAgent);

    String remoteAddr = null;
    if (request instanceof ServletServerHttpRequest servletReq) {
      remoteAddr = servletReq.getServletRequest().getRemoteAddr();
    }
    attributes.put(ATTR_REMOTE_ADDR, remoteAddr);
    return true;
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {}
}
