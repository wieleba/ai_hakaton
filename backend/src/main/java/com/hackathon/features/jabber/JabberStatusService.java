package com.hackathon.features.jabber;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Probes the configured XMPP servers:
 * <ul>
 *   <li>TCP reachability on the client + S2S ports</li>
 *   <li>HTTP API calls for registered/online user counts and live S2S connection counts</li>
 * </ul>
 * Every HTTP / socket operation is best-effort — any failure renders as
 * {@code reachable = false} / {@code null} counts without throwing to callers.
 */
@Service
@Slf4j
public class JabberStatusService {

    private static final Duration PROBE_TIMEOUT = Duration.ofMillis(1500);

    private final JabberProperties props;
    private final RestClient http;

    public JabberStatusService(JabberProperties props) {
        this.props = props;
        var settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(PROBE_TIMEOUT)
                .withReadTimeout(PROBE_TIMEOUT);
        this.http = RestClient.builder()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }

    public List<JabberServerStatus> statuses() {
        return props.servers().stream().map(this::probe).toList();
    }

    private JabberServerStatus probe(JabberProperties.Server s) {
        boolean clientReachable = tcpProbe(s.clientHost(), s.clientPort());
        boolean s2sReachable = tcpProbe(s.s2sHost(), s.s2sPort());

        Integer registeredUsers = null;
        Integer onlineUsers = null;
        Integer outgoingS2s = null;
        Integer incomingS2s = null;
        boolean httpApiReachable = false;

        if (clientReachable) {
            try {
                registeredUsers = callStatsHost(s, "registeredusers");
                onlineUsers = callStatsHost(s, "onlineusers");
                outgoingS2s = apiCall(s, "/api/outgoing_s2s_number", Map.of());
                incomingS2s = apiCall(s, "/api/incoming_s2s_number", Map.of());
                httpApiReachable = true;
            } catch (RuntimeException e) {
                log.debug("Jabber[{}] HTTP API probe failed: {}", s.label(), e.toString());
            }
        }

        return new JabberServerStatus(
                s.label(),
                s.domain(),
                s.clientHost(),
                s.clientPort(),
                s.s2sPort(),
                clientReachable,
                s2sReachable,
                httpApiReachable,
                registeredUsers,
                onlineUsers,
                outgoingS2s,
                incomingS2s);
    }

    private boolean tcpProbe(String host, int port) {
        try (Socket sock = new Socket()) {
            sock.connect(new InetSocketAddress(host, port), (int) PROBE_TIMEOUT.toMillis());
            return true;
        } catch (Exception e) {
            log.debug("Jabber TCP probe {}:{} failed: {}", host, port, e.toString());
            return false;
        }
    }

    private Integer callStatsHost(JabberProperties.Server s, String name) {
        return apiCall(s, "/api/stats_host", Map.of("name", name, "host", s.domain()));
    }

    private Integer apiCall(JabberProperties.Server s, String path, Map<String, String> body) {
        String basic = Base64.getEncoder()
                .encodeToString((s.adminJid() + ":" + s.adminPassword()).getBytes(StandardCharsets.UTF_8));
        String json = toJson(body);
        log.debug("Jabber[{}] POST {}{} body={}", s.label(), s.httpUrl(), path, json);
        String response = http.post()
                .uri(s.httpUrl() + path)
                .header("Authorization", "Basic " + basic)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(json)
                .retrieve()
                .body(String.class);
        log.debug("Jabber[{}] response: {}", s.label(), response);
        return response == null ? null : Integer.parseInt(response.trim());
    }

    private static String toJson(Map<String, String> body) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var e : body.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append('"').append(e.getKey()).append("\":\"").append(e.getValue()).append('"');
        }
        return sb.append("}").toString();
    }
}
