package com.hackathon.features.jabber;

/**
 * Well-known XML element attached to messages the outgoing relay sends, so
 * the inbound bridge can identify — and skip — its own relayed traffic.
 */
public final class JabberBridgeStanza {
    public static final String MARKER_ELEMENT = "bridge";
    public static final String MARKER_NAMESPACE = "urn:chat:bridge:0";

    private JabberBridgeStanza() {}
}
