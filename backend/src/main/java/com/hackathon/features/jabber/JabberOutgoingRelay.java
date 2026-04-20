package com.hackathon.features.jabber;

import com.hackathon.features.dms.DmBridgeEvents;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.StandardExtensionElement;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;

/**
 * Listens for {@link DmBridgeEvents.DmCreated} and relays the message into
 * XMPP.
 *
 * <p>Primary path: send the stanza <em>through the sender's own bridge
 * Smack connection</em>. This makes ejabberd treat the message as a
 * user-originated send and fan out XEP-0280 SENT carbons to the user's
 * other resources (e.g. Psi+), which is how the sent line ends up visible
 * in the user's XMPP client.
 *
 * <p>Fallback path: admin-authenticated {@code /api/send_stanza}. Used when
 * the sender's bridge session isn't open (backend restart, transient
 * disconnect, user not JID-safe). SENT carbons are not fanned out on this
 * path — Psi+ won't see the sent line until the bridge reconnects — but
 * the recipient still receives it.
 *
 * <p>Runs AFTER_COMMIT so the outgoing HTTP / stanza send doesn't hold the
 * DM transaction open. XMPP delivery failure never surfaces back to the
 * caller — DM was already persisted before the event fired.
 */
@Component
@Slf4j
public class JabberOutgoingRelay {

    private final UserService userService;
    private final JabberProvisioningService provisioning;
    private final JabberIncomingBridge bridge;
    private final RestClient http;

    public JabberOutgoingRelay(
            UserService userService,
            JabberProvisioningService provisioning,
            JabberIncomingBridge bridge) {
        this.userService = userService;
        this.provisioning = provisioning;
        this.bridge = bridge;
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

        if (relayThroughSenderSession(sender.getId(), toJid, body)) {
            log.debug("JabberOutgoingRelay: relayed via session {} → {}", fromJid, toJid);
            return;
        }
        // Fallback: admin API. Recipient still sees it; SENT carbon to Psi+
        // won't fan out on this path.
        try {
            relayViaAdminApi(s, fromJid, toJid, body);
            log.debug("JabberOutgoingRelay: relayed via admin API {} → {}", fromJid, toJid);
        } catch (RuntimeException e) {
            log.warn("JabberOutgoingRelay: failed to relay {} → {}: {}", fromJid, toJid, e.toString());
        }
    }

    /**
     * Build a chat {@link Message} with the bridge marker and hand it to the
     * sender's Smack connection. Returns false if no session is available so
     * the caller can fall back.
     */
    private boolean relayThroughSenderSession(java.util.UUID senderId, String toJid, String body) {
        Message msg = new Message();
        msg.setType(Message.Type.chat);
        msg.setBody(body);
        msg.addExtension(StandardExtensionElement.builder(
                        JabberBridgeStanza.MARKER_ELEMENT, JabberBridgeStanza.MARKER_NAMESPACE)
                .build());
        return bridge.sendFromUser(senderId, toJid, msg);
    }

    private void relayViaAdminApi(
            JabberProperties.Server s, String fromJid, String toJid, String body) {
        String stanza = "<message type='chat'>"
                + "<body>" + escapeXml(body) + "</body>"
                + "<bridge xmlns='" + JabberBridgeStanza.MARKER_NAMESPACE + "'/>"
                + "</message>";
        callAdminApi(s, "/api/send_stanza", Map.of(
                "from", fromJid,
                "to", toJid,
                "stanza", stanza));
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
