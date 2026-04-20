package com.hackathon.features.jabber;

import com.hackathon.features.dms.DirectMessageService;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Chat ↔ XMPP bridge — inbound direction. Maintains one long-lived Smack
 * connection per Chat user that has a provisioned JID (see
 * {@link JabberProvisioningService}). When an XMPP chat message arrives at
 * that user's JID, the bridge converts it to a Chat DM via
 * {@link DirectMessageService#receiveFromXmppBridge}.
 *
 * <h3>Loop prevention</h3>
 * The receiver calls {@code receiveFromXmppBridge} which intentionally does
 * <em>not</em> publish {@code DmBridgeEvents.DmCreated}, so the outgoing relay
 * never fires for a bridged-in message.
 *
 * <h3>Multi-resource handling</h3>
 * The bridge connects with resource {@code chat-bridge} at priority
 * {@link #BRIDGE_PRIORITY}. Real XMPP clients like Psi+ default to priority 0;
 * by setting the bridge low, routed messages prefer the real client when the
 * user is online in both. Messages go to the bridge when no client is
 * connected, delivering them into the Chat web UI.
 */
@Component
@Slf4j
public class JabberIncomingBridge {

    /**
     * Bridge presence priority.
     *
     * <p>XMPP spec: bare-JID message routing excludes any resource with a
     * <em>negative</em> priority (RFC 6121 §8.1.2). So a naive "set the bridge
     * to -64 so Psi+ wins" breaks the incoming bridge entirely — the server
     * drops bridge-destined messages when no other resource is online.
     *
     * <p>Using 0 makes the bridge eligible for routing whether or not the
     * user is also online in a real client. If they are, ejabberd ties broken
     * by most-recent-resource; the Chat UI may or may not see the carbon. A
     * proper fix for multi-resource deployments is XEP-0280 Message Carbons
     * on the bridge connection — tracked as a follow-up, not blocking.
     */
    private static final int BRIDGE_PRIORITY = 0;

    /** Reconnect delay for transient failures. */
    private static final long RECONNECT_DELAY_SECONDS = 5;

    private final JabberProperties props;
    private final JabberProvisioningService provisioning;
    private final UserRepository userRepository;
    private final DirectMessageService directMessageService;

    private final ConcurrentHashMap<UUID, XMPPTCPConnection> connections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "jabber-bridge");
                t.setDaemon(true);
                return t;
            });

    public JabberIncomingBridge(
            JabberProperties props,
            JabberProvisioningService provisioning,
            UserRepository userRepository,
            DirectMessageService directMessageService) {
        this.props = props;
        this.provisioning = provisioning;
        this.userRepository = userRepository;
        this.directMessageService = directMessageService;
        SmackConfiguration.setDefaultReplyTimeout(10_000);
    }

    @PostConstruct
    void start() {
        scheduler.execute(this::openAllProvisionedConnections);
    }

    @PreDestroy
    void stop() {
        connections.forEach((id, conn) -> disconnect(conn));
        connections.clear();
        scheduler.shutdownNow();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserProvisioned(JabberEvents.UserProvisioned event) {
        scheduler.execute(() -> openFor(event.userId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserDeprovisioned(JabberEvents.UserDeprovisioned event) {
        XMPPTCPConnection conn = connections.remove(event.userId());
        if (conn != null) disconnect(conn);
    }

    private void openAllProvisionedConnections() {
        List<User> users = userRepository.findByXmppPasswordIsNotNull();
        log.info("JabberIncomingBridge: opening connections for {} provisioned users", users.size());
        for (User u : users) {
            try {
                openFor(u);
            } catch (RuntimeException e) {
                log.warn("JabberIncomingBridge: skipping {} due to error: {}", u.getUsername(), e.toString());
            }
        }
    }

    private void openFor(UUID userId) {
        userRepository.findById(userId).ifPresent(this::openFor);
    }

    private void openFor(User user) {
        if (user.getXmppPassword() == null) return;
        if (connections.containsKey(user.getId())) return;
        Optional<JabberProperties.Server> primary = provisioning.primary();
        if (primary.isEmpty()) return;
        JabberProperties.Server s = primary.get();
        try {
            XMPPTCPConnection conn = connect(user, s);
            connections.put(user.getId(), conn);
            log.info("JabberIncomingBridge: connected {}@{}", user.getUsername(), s.domain());
        } catch (Exception e) {
            log.warn(
                    "JabberIncomingBridge: failed to connect {}@{}: {} — scheduling retry in {}s",
                    user.getUsername(),
                    s.domain(),
                    e.toString(),
                    RECONNECT_DELAY_SECONDS);
            scheduler.schedule(
                    () -> openFor(user.getId()), RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
        }
    }

    private XMPPTCPConnection connect(User user, JabberProperties.Server s) throws Exception {
        XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder()
                .setXmppDomain(JidCreate.domainBareFrom(s.domain()))
                .setHost(s.clientHost())
                .setPort(s.clientPort())
                .setUsernameAndPassword(user.getUsername(), user.getXmppPassword())
                .setResource(Resourcepart.from("chat-bridge"))
                .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled)
                .setConnectTimeout(5_000)
                .build();
        XMPPTCPConnection conn = new XMPPTCPConnection(config);
        StanzaFilter chatFilter = new StanzaTypeFilter(Message.class);
        StanzaListener listener = stanza -> handleIncomingStanza(user, (Message) stanza);
        conn.addAsyncStanzaListener(listener, chatFilter);
        conn.connect();
        conn.login();
        conn.sendStanza(buildLowPriorityPresence());
        return conn;
    }

    private Presence buildLowPriorityPresence() {
        Presence p = new Presence(Presence.Type.available);
        p.setPriority(BRIDGE_PRIORITY);
        return p;
    }

    private void disconnect(XMPPTCPConnection conn) {
        try {
            conn.disconnect();
        } catch (Exception ignored) {
            // nothing useful to do on close failure
        }
    }

    private void handleIncomingStanza(User recipient, Message msg) {
        log.debug("JabberIncomingBridge: stanza for {} type={} from={}",
                recipient.getUsername(), msg.getType(), msg.getFrom());
        // Only process chat-type messages with a body. Skip typing indicators,
        // receipts, and anything else that is not a rendered chat line.
        if (msg.getType() != Message.Type.chat && msg.getType() != Message.Type.normal) return;
        String body = msg.getBody();
        if (body == null || body.isBlank()) return;

        // Skip our own outgoing relay's stanzas (marked with <bridge xmlns='...'/>).
        // Without this, every DM sent from Chat UI would be persisted twice: once
        // by DirectMessageService.send() and again by receiveFromXmppBridge()
        // when ejabberd delivers the relayed copy to the recipient's Smack bot.
        if (msg.getExtensionElement(
                JabberBridgeStanza.MARKER_ELEMENT, JabberBridgeStanza.MARKER_NAMESPACE)
                != null) {
            return;
        }

        EntityBareJid fromJid;
        try {
            fromJid = msg.getFrom().asEntityBareJidOrThrow();
        } catch (Exception e) {
            log.debug("JabberIncomingBridge: ignoring message with malformed from: {}", msg.getFrom());
            return;
        }
        String senderLocal = fromJid.getLocalpart().toString();
        String senderDomain = fromJid.getDomain().toString();

        Optional<JabberProperties.Server> primary = provisioning.primary();
        if (primary.isEmpty()) return;
        String primaryDomain = primary.get().domain();

        // Only bridge messages whose sender maps to an existing Chat user on the
        // primary domain. Cross-domain XMPP (e.g. bob@chat-b.local who is not a
        // Chat user) is intentionally not bridged — see feature/jabber scope
        // B.narrow. Those messages stay in pure XMPP and are visible only in
        // clients directly attached to the recipient's JID.
        if (!primaryDomain.equals(senderDomain)) {
            log.debug(
                    "JabberIncomingBridge: dropping cross-domain message {} → {}@{}",
                    fromJid,
                    recipient.getUsername(),
                    primaryDomain);
            return;
        }
        Optional<User> sender = userRepository.findByUsername(senderLocal);
        if (sender.isEmpty()) {
            log.debug(
                    "JabberIncomingBridge: sender {}@{} is not a Chat user — skipping",
                    senderLocal,
                    senderDomain);
            return;
        }
        if (sender.get().getId().equals(recipient.getId())) {
            // Self-carbon — ignore.
            return;
        }

        try {
            directMessageService.receiveFromXmppBridge(sender.get().getId(), recipient.getId(), body);
            log.debug(
                    "JabberIncomingBridge: delivered XMPP DM {} → {} into Chat",
                    sender.get().getUsername(),
                    recipient.getUsername());
        } catch (RuntimeException e) {
            log.warn(
                    "JabberIncomingBridge: failed to persist bridged DM {} → {}: {}",
                    sender.get().getUsername(),
                    recipient.getUsername(),
                    e.toString());
        }
    }
}
