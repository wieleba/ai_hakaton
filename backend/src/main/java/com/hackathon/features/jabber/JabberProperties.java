package com.hackathon.features.jabber;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Holds the two (or more) configured XMPP servers the chat stack federates with.
 * Bound from {@code app.jabber.*} in application.yml.
 */
@ConfigurationProperties(prefix = "app.jabber")
public record JabberProperties(List<Server> servers) {

    public record Server(
            String label,
            String domain,
            String clientHost,
            int clientPort,
            String s2sHost,
            int s2sPort,
            String httpUrl,
            String adminJid,
            String adminPassword) {}
}
