package com.hackathon.features.jabber;

/** Snapshot of one XMPP server's reachability + metrics. Null counts on API failure. */
public record JabberServerStatus(
        String label,
        String domain,
        String host,
        int clientPort,
        int s2sPort,
        boolean clientReachable,
        boolean s2sReachable,
        boolean httpApiReachable,
        Integer registeredUsers,
        Integer onlineUsers,
        Integer outgoingS2sConnections,
        Integer incomingS2sConnections) {}
