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
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.carbons.CarbonManager;
import org.jivesoftware.smackx.carbons.packet.CarbonExtension;
import org.jivesoftware.smackx.forward.packet.Forwarded;
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
 * never fires for a bridged-in message. Additionally, every outbound relay's
 * stanza carries a {@code <bridge xmlns='urn:chat:bridge:0'/>} marker; the
 * inbound listener discards any message (direct or carbon-wrapped) that has
 * the marker on its body.
 *
 * <h3>Multi-resource handling via XEP-0280 Message Carbons</h3>
 * The bridge connects with resource {@code chat-bridge} at priority
 * {@link #BRIDGE_PRIORITY} (0) and enables Message Carbons after login. That
 * way, when a user is concurrently online in the bridge and some other
 * client (e.g. Psi+), ejabberd routes each message to one resource and
 * carbon-copies the other — so the Chat web UI sees every inbound or sent
 * message regardless of which resource actually won the routing race.
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
     * Using 0 keeps the bridge eligible; Message Carbons (enabled after
     * login) then make the bridge always receive a copy even when routing
     * picks some other resource.
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
        enableCarbons(conn, user);
        conn.sendStanza(buildLowPriorityPresence());
        return conn;
    }

    private void enableCarbons(XMPPTCPConnection conn, User user) {
        try {
            CarbonManager.getInstanceFor(conn).enableCarbons();
            log.debug("JabberIncomingBridge: carbons enabled for {}", user.getUsername());
        } catch (Exception e) {
            // Server doesn't support Carbons or another transient issue — degrade
            // gracefully to the single-resource flow. The bridge still works when
            // no other client is concurrently online for this user.
            log.warn(
                    "JabberIncomingBridge: failed to enable carbons for {}: {}",
                    user.getUsername(),
                    e.toString());
        }
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

    /**
     * Send a chat stanza <em>from</em> the given user's own bridge Smack
     * session. Used by {@link JabberOutgoingRelay} so ejabberd treats the
     * message as user-originated and fans out XEP-0280 SENT carbons to the
     * user's other resources (Psi+ etc.) — an admin-API {@code send_stanza}
     * injection wouldn't trigger that.
     *
     * @return {@code true} if the stanza was sent through the user's Smack
     *     connection; {@code false} if no live bridge session exists (the
     *     caller should fall back to an alternate delivery path).
     */
    public boolean sendFromUser(UUID senderUserId, String toJid, Message stanza) {
        XMPPTCPConnection conn = connections.get(senderUserId);
        if (conn == null || !conn.isConnected() || !conn.isAuthenticated()) {
            return false;
        }
        try {
            stanza.setTo(org.jxmpp.jid.impl.JidCreate.entityBareFrom(toJid));
            conn.sendStanza(stanza);
            return true;
        } catch (Exception e) {
            log.warn("JabberIncomingBridge: sendFromUser failed for {}: {}", senderUserId, e.toString());
            return false;
        }
    }

    private void handleIncomingStanza(User bridgeUser, Message msg) {
        log.debug(
                "JabberIncomingBridge: stanza for {} type={} from={}",
                bridgeUser.getUsername(),
                msg.getType(),
                msg.getFrom());

        // XEP-0280 Message Carbons: unwrap before applying routing rules. The
        // outer wrapper has no body; the interesting stanza lives inside
        // <received>/<sent><forwarded><message/>...
        CarbonExtension carbon = CarbonExtension.from(msg);
        if (carbon != null) {
            Forwarded<Message> forwarded = carbon.getForwarded();
            if (forwarded == null) return;
            Message inner = forwarded.getForwardedStanza();
            if (inner == null) return;
            boolean isSent = carbon.getDirection() == CarbonExtension.Direction.sent;
            processBody(bridgeUser, inner, isSent);
            return;
        }
        // Non-carbon direct message — ejabberd routed the stanza to this
        // bridge resource because it was the only/chosen target.
        processBody(bridgeUser, msg, false);
    }

    /**
     * Route a concrete message (direct or carbon-inner) into the Chat DM
     * flow. The {@code bridgeUser} is the owner of this Smack connection —
     * for context, logs, and a sanity check — but the routing is driven by
     * the message's own {@code from}/{@code to}.
     */
    private void processBody(User bridgeUser, Message msg, boolean isSentCarbon) {
        if (msg.getType() != Message.Type.chat && msg.getType() != Message.Type.normal) return;
        String body = msg.getBody();
        if (body == null || body.isBlank()) return;

        // Skip our own outgoing relay's stanzas. Preserved through carbon
        // wrapping because <bridge> sits on the body-carrying <message/>.
        if (msg.getExtensionElement(
                JabberBridgeStanza.MARKER_ELEMENT, JabberBridgeStanza.MARKER_NAMESPACE)
                != null) {
            return;
        }

        EntityBareJid fromJid;
        EntityBareJid toJid;
        try {
            fromJid = msg.getFrom().asEntityBareJidOrThrow();
            toJid = msg.getTo().asEntityBareJidOrThrow();
        } catch (Exception e) {
            log.debug(
                    "JabberIncomingBridge: malformed from/to: {} / {}", msg.getFrom(), msg.getTo());
            return;
        }

        Optional<JabberProperties.Server> primary = provisioning.primary();
        if (primary.isEmpty()) return;
        String primaryDomain = primary.get().domain();

        // Scope B.narrow: both endpoints must live on the primary domain AND
        // both local parts must map to Chat users. Cross-domain XMPP stays in
        // pure XMPP, not in Chat DMs.
        if (!primaryDomain.equals(fromJid.getDomain().toString())) {
            log.debug("JabberIncomingBridge: dropping cross-domain message from {}", fromJid);
            return;
        }
        if (!primaryDomain.equals(toJid.getDomain().toString())) {
            log.debug("JabberIncomingBridge: dropping cross-domain message to {}", toJid);
            return;
        }

        String senderLocal = fromJid.getLocalpart().toString();
        String recipLocal = toJid.getLocalpart().toString();
        Optional<User> sender = userRepository.findByUsername(senderLocal);
        Optional<User> recip = userRepository.findByUsername(recipLocal);
        if (sender.isEmpty() || recip.isEmpty()) {
            log.debug(
                    "JabberIncomingBridge: sender {} or recipient {} is not a Chat user — skipping",
                    senderLocal,
                    recipLocal);
            return;
        }
        if (sender.get().getId().equals(recip.get().getId())) {
            // Self-message — not useful for the bridge.
            return;
        }

        try {
            directMessageService.receiveFromXmppBridge(
                    sender.get().getId(), recip.get().getId(), body);
            log.debug(
                    "JabberIncomingBridge: delivered XMPP DM {} → {} into Chat{}",
                    sender.get().getUsername(),
                    recip.get().getUsername(),
                    isSentCarbon ? " (via SENT carbon)" : "");
        } catch (RuntimeException e) {
            log.warn(
                    "JabberIncomingBridge: failed to persist bridged DM {} → {}: {}",
                    sender.get().getUsername(),
                    recip.get().getUsername(),
                    e.toString());
        }
    }
}
