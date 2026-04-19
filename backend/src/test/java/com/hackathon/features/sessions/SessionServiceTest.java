package com.hackathon.features.sessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hackathon.features.presence.InMemoryPresenceService;
import com.hackathon.features.presence.PresenceService;
import com.hackathon.shared.security.TokenRevocationService;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * Pure unit test — avoids booting a Spring context. Uses a real
 * InMemoryPresenceService (self-contained, no Spring needed) for presence and
 * mocks the collaborator interactions we care about.
 */
class SessionServiceTest {

  private PresenceService presenceService;
  private TokenRevocationService tokenRevocationService;
  private SessionDisconnector sessionDisconnector;
  private SimpMessagingTemplate messagingTemplate;
  private SessionService sessionService;

  @BeforeEach
  void setUp() {
    presenceService = new InMemoryPresenceService();
    tokenRevocationService = mock(TokenRevocationService.class);
    sessionDisconnector = mock(SessionDisconnector.class);
    messagingTemplate = mock(SimpMessagingTemplate.class);
    sessionService =
        new SessionService(
            presenceService, tokenRevocationService, sessionDisconnector, messagingTemplate);
  }

  @Test
  void listReturnsAllMySessions() {
    UUID me = UUID.randomUUID();
    presenceService.markOnline(me, "s1", "UA1", "1.1.1.1", "h1");
    presenceService.markOnline(me, "s2", "UA2", "2.2.2.2", "h2");

    List<SessionView> rows = sessionService.list(me);

    assertThat(rows).extracting(SessionView::sessionId).containsExactlyInAnyOrder("s1", "s2");
  }

  @Test
  void logoutOfUnknownSessionThrows() {
    UUID me = UUID.randomUUID();
    assertThatThrownBy(() -> sessionService.logout(me, "ghost"))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void logoutRevokesTokenDisconnectsAndRemovesFromPresence() {
    UUID me = UUID.randomUUID();
    presenceService.markOnline(me, "s1", "UA1", "1.1.1.1", "h1");

    sessionService.logout(me, "s1");

    verify(tokenRevocationService).revoke(eq("h1"), anyLong());
    verify(sessionDisconnector).disconnect("s1");
    verify(messagingTemplate).convertAndSendToUser(eq(me.toString()), eq("/queue/sessions"), any());
    assertThat(sessionService.list(me)).isEmpty();
  }

  @Test
  void logoutOthersKeepsCurrentSessionAndReturnsCount() {
    UUID me = UUID.randomUUID();
    presenceService.markOnline(me, "s1", "UA1", "1.1.1.1", "h1");
    presenceService.markOnline(me, "s2", "UA2", "2.2.2.2", "h2");
    presenceService.markOnline(me, "s3", "UA3", "3.3.3.3", "h3");

    int count = sessionService.logoutOthers(me, "s2");

    assertThat(count).isEqualTo(2);
    verify(sessionDisconnector).disconnect("s1");
    verify(sessionDisconnector).disconnect("s3");
    verify(sessionDisconnector, never()).disconnect("s2");
    assertThat(sessionService.list(me)).extracting(SessionView::sessionId).containsExactly("s2");
  }

  @Test
  void logoutOthersWithStaleCurrentIdThrowsAndNukesNothing() {
    UUID me = UUID.randomUUID();
    presenceService.markOnline(me, "s1", "UA1", "1.1.1.1", "h1");
    presenceService.markOnline(me, "s2", "UA2", "2.2.2.2", "h2");

    assertThatThrownBy(() -> sessionService.logoutOthers(me, "ghost"))
        .isInstanceOf(NoSuchElementException.class);

    verifyNoInteractions(sessionDisconnector);
    assertThat(sessionService.list(me)).extracting(SessionView::sessionId)
        .containsExactlyInAnyOrder("s1", "s2");
  }
}
