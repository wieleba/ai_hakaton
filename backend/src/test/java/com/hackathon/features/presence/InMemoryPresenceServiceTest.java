package com.hackathon.features.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class InMemoryPresenceServiceTest {
  @Autowired InMemoryPresenceService service;

  @Test
  void noSessions_isOffline() {
    assertEquals(PresenceState.OFFLINE, service.aggregate(UUID.randomUUID()));
  }

  @Test
  void oneActiveSession_isOnline() {
    UUID u = UUID.randomUUID();
    service.markOnline(u, "s1", null, null, null);
    assertEquals(PresenceState.ONLINE, service.aggregate(u));
  }

  @Test
  void oneIdleSession_isAfk() {
    UUID u = UUID.randomUUID();
    service.markOnline(u, "s1", null, null, null);
    service.markAfk(u, "s1");
    assertEquals(PresenceState.AFK, service.aggregate(u));
  }

  @Test
  void oneActiveOneIdle_isOnline() {
    UUID u = UUID.randomUUID();
    service.markOnline(u, "s1", null, null, null);
    service.markOnline(u, "s2", null, null, null);
    service.markAfk(u, "s2");
    assertEquals(PresenceState.ONLINE, service.aggregate(u));
  }

  @Test
  void allIdle_isAfk() {
    UUID u = UUID.randomUUID();
    service.markOnline(u, "s1", null, null, null);
    service.markOnline(u, "s2", null, null, null);
    service.markAfk(u, "s1");
    service.markAfk(u, "s2");
    assertEquals(PresenceState.AFK, service.aggregate(u));
  }

  @Test
  void markOffline_removesSession() {
    UUID u = UUID.randomUUID();
    service.markOnline(u, "s1", null, null, null);
    service.markOnline(u, "s2", null, null, null);
    service.markOffline(u, "s1");
    assertEquals(PresenceState.ONLINE, service.aggregate(u));
    service.markOffline(u, "s2");
    assertEquals(PresenceState.OFFLINE, service.aggregate(u));
  }

  @Test
  void snapshot_returnsStatePerId() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID c = UUID.randomUUID();
    service.markOnline(a, "s1", null, null, null);
    service.markOnline(b, "s1", null, null, null);
    service.markAfk(b, "s1");
    var snap = service.snapshot(List.of(a, b, c));
    assertEquals(PresenceState.ONLINE, snap.get(a));
    assertEquals(PresenceState.AFK, snap.get(b));
    assertEquals(PresenceState.OFFLINE, snap.get(c));
  }

  @Test
  void evictStale_removesOnlyOldOwnedSessions() throws InterruptedException {
    UUID u = UUID.randomUUID();
    service.markOnline(u, "s1", null, null, null);
    Thread.sleep(10);
    long cutoff = System.currentTimeMillis() + 1000; // anything older than "1s in the future" is stale
    service.evictStaleOwnedBy(service.getInstanceId(), cutoff);
    assertEquals(PresenceState.OFFLINE, service.aggregate(u));
  }

  @Test
  void evictStale_skipsFreshOwnedSessions() {
    UUID u = UUID.randomUUID();
    service.markOnline(u, "s1", null, null, null);
    long cutoff = System.currentTimeMillis() - 1_000_000; // nothing is older than "forever ago"
    service.evictStaleOwnedBy(service.getInstanceId(), cutoff);
    assertEquals(PresenceState.ONLINE, service.aggregate(u));
  }

  @Test
  void heartbeat_updatesLastSeen_preventsEviction() throws InterruptedException {
    UUID u = UUID.randomUUID();
    service.markOnline(u, "s1", null, null, null);
    Thread.sleep(5);
    service.heartbeat(u, "s1");
    long cutoff = System.currentTimeMillis() - 1;
    service.evictStaleOwnedBy(service.getInstanceId(), cutoff);
    assertEquals(PresenceState.ONLINE, service.aggregate(u));
  }

  @Test
  void listSessionsReturnsMetadataProvidedToMarkOnline() {
    UUID user = UUID.randomUUID();
    service.markOnline(user, "s1", "Chrome/120", "10.0.0.4", "hashA");

    List<com.hackathon.features.sessions.SessionView> rows = service.listSessions(user);

    assertThat(rows).hasSize(1);
    var row = rows.get(0);
    assertThat(row.sessionId()).isEqualTo("s1");
    assertThat(row.userAgent()).isEqualTo("Chrome/120");
    assertThat(row.remoteAddr()).isEqualTo("10.0.0.4");
    assertThat(row.tokenHash()).isEqualTo("hashA");
    assertThat(row.idle()).isFalse();
    assertThat(row.connectedAt()).isNotNull();
    assertThat(row.lastSeen()).isNotNull();
  }

  @Test
  void heartbeatAndMarkAfkPreserveMetadata() {
    UUID user = UUID.randomUUID();
    service.markOnline(user, "s1", "Chrome/120", "10.0.0.4", "hashA");
    service.heartbeat(user, "s1");
    service.markAfk(user, "s1");

    var row = service.listSessions(user).get(0);
    assertThat(row.userAgent()).isEqualTo("Chrome/120");
    assertThat(row.remoteAddr()).isEqualTo("10.0.0.4");
    assertThat(row.tokenHash()).isEqualTo("hashA");
    assertThat(row.idle()).isTrue();
  }
}
