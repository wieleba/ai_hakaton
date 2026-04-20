package com.hackathon.features.dms;

import java.util.UUID;

/**
 * DM lifecycle events published by {@link DirectMessageService} for off-transaction
 * consumers (e.g. the Chat↔XMPP bridge). Use dedicated event records rather than
 * reusing {@code MessageEmbedEvents} so the intent of each listener is obvious.
 *
 * <p>Consumers should listen with
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} to avoid holding the
 * message-send transaction open during external I/O.
 */
public final class DmBridgeEvents {
    private DmBridgeEvents() {}

    /** Fired after a DM row is persisted, before the outer send-tx commits. */
    public record DmCreated(UUID senderId, UUID recipientId, String text) {}
}
