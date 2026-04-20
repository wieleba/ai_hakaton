package com.hackathon.features.jabber;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * On first application-ready event, scans every Chat user whose XMPP
 * bridge hasn't been provisioned and provisions them in the background.
 * Needed because {@code UserService.registerUser} only provisions
 * <em>newly-registered</em> users — existing rows (pre-V14 or created when
 * the Jabber feature was disabled) would otherwise never get an XMPP
 * bridge, silently breaking the Chat → XMPP outgoing relay for them.
 *
 * <p>Runs on a dedicated daemon thread — the backfill walks every
 * unprovisioned user with a ~50 ms throttle per ejabberd call, which
 * could stretch to tens of seconds on a large DB. We don't want that
 * sitting on the main event-loop thread once the app is "ready".
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JabberBackfillRunner {

    private final JabberProvisioningService provisioning;

    @EventListener(ApplicationReadyEvent.class)
    public void backfillOnStartup() {
        Thread t = new Thread(this::runBackfill, "jabber-backfill");
        t.setDaemon(true);
        t.start();
    }

    private void runBackfill() {
        try {
            provisioning.backfillAllUnprovisioned();
        } catch (RuntimeException e) {
            log.warn("JabberBackfillRunner: backfill aborted: {}", e.toString());
        }
    }
}
