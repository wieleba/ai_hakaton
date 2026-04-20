package com.hackathon.features.jabber;

import com.hackathon.features.dms.DmBridgeEvents;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;

/**
 * Listens for {@link DmBridgeEvents.DmCreated} and relays the message into
 * XMPP by posting a {@code <message>} stanza (with a bridge-marker child) to
 * the primary ejabberd server's {@code /api/send_stanza}.
 *
 * <p>Only runs AFTER the DM commit so the blocking HTTP call doesn't hold the
 * send-tx open (same pattern as {@code EmbedService}). Both sender and
 * recipient must have a provisioned JID — if either doesn't, the relay is a
 * no-op. XMPP delivery failure never surfaces back to the caller.
 */
@Component
@Slf4j
public class JabberOutgoingRelay {

    private final UserService userService;
    private final JabberProvisioningService provisioning;
    private final RestClient http;

    public JabberOutgoingRelay(UserService userService, JabberProvisioningService provisioning) {
        this.userService = userService;
        this.provisioning = provisioning;
        var settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(java.time.Duration.ofMillis(1500))
                .withReadTimeout(java.time.Duration.ofMillis(1500));
        this.http = RestClient.builder()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDmCreated(DmBridgeEvents.DmCreated event) {
        Optional<JabberProperties.Server> primary = provisioning.primary();
        if (primary.isEmpty()) return;
        JabberProperties.Server s = primary.get();

        User sender = safeLoad(event.senderId());
        User recipient = safeLoad(event.recipientId());
        if (sender == null || recipient == null) return;
        if (sender.getXmppPassword() == null || recipient.getXmppPassword() == null) {
            // One side has no XMPP bridge — skip. This is expected for users with
            // unsafe JID local-parts or when provisioning failed.
            return;
        }
        String fromJid = sender.getUsername() + "@" + s.domain();
        String toJid = recipient.getUsername() + "@" + s.domain();
        String body = event.text() == null ? "" : event.text();
        // send_stanza (vs send_message) lets us attach a bridge marker so the
        // inbound Smack listener can identify — and skip — its own relayed
        // traffic. Without it, every DM sent from Chat UI would be persisted
        // twice: once by DirectMessageService.send() and again by
        // receiveFromXmppBridge() when ejabberd delivers the relay back.
        String stanza = "<message type='chat'>"
                + "<body>" + escapeXml(body) + "</body>"
                + "<bridge xmlns='" + JabberBridgeStanza.MARKER_NAMESPACE + "'/>"
                + "</message>";
        try {
            callAdminApi(s, "/api/send_stanza", Map.of(
                    "from", fromJid,
                    "to", toJid,
                    "stanza", stanza));
            log.debug("JabberOutgoingRelay: relayed DM {} → {}", fromJid, toJid);
        } catch (RuntimeException e) {
            log.warn("JabberOutgoingRelay: failed to relay {} → {}: {}", fromJid, toJid, e.toString());
        }
    }

    private static String escapeXml(String v) {
        return v.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private User safeLoad(java.util.UUID id) {
        try {
            return userService.getUserById(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void callAdminApi(JabberProperties.Server s, String path, Map<String, String> body) {
        String basic = Base64.getEncoder()
                .encodeToString(
                        (s.adminJid() + ":" + s.adminPassword()).getBytes(StandardCharsets.UTF_8));
        http.post()
                .uri(s.httpUrl() + path)
                .header("Authorization", "Basic " + basic)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(toJson(body))
                .retrieve()
                .toBodilessEntity();
    }

    private static String toJson(Map<String, String> body) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var e : body.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append('"').append(e.getKey()).append("\":\"").append(escape(e.getValue())).append('"');
        }
        return sb.append("}").toString();
    }

    private static String escape(String v) {
        return v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
