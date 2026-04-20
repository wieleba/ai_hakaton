package com.hackathon.features.jabber;

import java.util.UUID;

/**
 * Lifecycle events for XMPP-bridge participation. Fired by
 * {@link JabberProvisioningService}, consumed by {@link JabberIncomingBridge}
 * so the bridge opens / closes long-lived Smack connections without polling.
 */
public final class JabberEvents {
    private JabberEvents() {}

    public record UserProvisioned(UUID userId) {}

    public record UserDeprovisioned(UUID userId) {}
}
