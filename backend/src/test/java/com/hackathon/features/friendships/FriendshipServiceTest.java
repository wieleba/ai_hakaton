package com.hackathon.features.friendships;

import static org.instancio.Instancio.create;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.hackathon.features.bans.UserBanRepository;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.websocket.FriendshipEventPublisher;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class FriendshipServiceTest {
  @Mock private FriendshipRepository friendshipRepository;
  @Mock private UserBanRepository userBanRepository;
  @Mock private UserService userService;
  @Mock private FriendshipEventPublisher eventPublisher;

  private FriendshipService service;

  private UUID meId;
  private UUID otherId;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service =
        new FriendshipService(friendshipRepository, userBanRepository, userService, eventPublisher);
    meId = UUID.randomUUID();
    otherId = UUID.randomUUID();
  }

  @Test
  void sendRequest_createsPendingWhenNoExistingRelationship() {
    User target = User.builder().id(otherId).username("bob").build();
    when(userService.getUserByUsername("bob")).thenReturn(target);
    when(friendshipRepository.findBetween(meId, otherId)).thenReturn(Optional.empty());
    when(userBanRepository.existsByBannerIdAndBannedId(any(), any())).thenReturn(false);
    when(friendshipRepository.save(any(Friendship.class)))
        .thenAnswer(inv -> inv.getArgument(0, Friendship.class));

    Friendship result = service.sendRequest(meId, "bob");

    assertEquals(Friendship.STATUS_PENDING, result.getStatus());
    assertEquals(meId, result.getRequesterId());
    assertEquals(otherId, result.getAddresseeId());
    verify(eventPublisher).publishRequestCreated(otherId, result);
  }

  @Test
  void sendRequest_autoAcceptsInversePending() {
    User target = User.builder().id(otherId).username("bob").build();
    Friendship existing =
        Friendship.builder()
            .id(UUID.randomUUID())
            .requesterId(otherId)
            .addresseeId(meId)
            .status(Friendship.STATUS_PENDING)
            .build();
    when(userService.getUserByUsername("bob")).thenReturn(target);
    when(friendshipRepository.findBetween(meId, otherId)).thenReturn(Optional.of(existing));
    when(userBanRepository.existsByBannerIdAndBannedId(any(), any())).thenReturn(false);
    when(friendshipRepository.save(any(Friendship.class)))
        .thenAnswer(inv -> inv.getArgument(0, Friendship.class));

    Friendship result = service.sendRequest(meId, "bob");

    assertEquals(Friendship.STATUS_ACCEPTED, result.getStatus());
    verify(eventPublisher).publishAccepted(otherId, meId, result);
    verify(eventPublisher).publishAccepted(meId, otherId, result);
  }

  @Test
  void sendRequest_rejectsSelf() {
    User me = User.builder().id(meId).username("me").build();
    when(userService.getUserByUsername("me")).thenReturn(me);

    assertThrows(IllegalArgumentException.class, () -> service.sendRequest(meId, "me"));
    verify(friendshipRepository, never()).save(any());
  }

  @Test
  void sendRequest_rejectsIfAlreadyFriends() {
    User target = User.builder().id(otherId).username("bob").build();
    Friendship existing =
        Friendship.builder()
            .requesterId(otherId)
            .addresseeId(meId)
            .status(Friendship.STATUS_ACCEPTED)
            .build();
    when(userService.getUserByUsername("bob")).thenReturn(target);
    when(friendshipRepository.findBetween(meId, otherId)).thenReturn(Optional.of(existing));
    when(userBanRepository.existsByBannerIdAndBannedId(any(), any())).thenReturn(false);

    assertThrows(IllegalArgumentException.class, () -> service.sendRequest(meId, "bob"));
  }

  @Test
  void sendRequest_rejectsIfBanExistsEitherDirection() {
    User target = User.builder().id(otherId).username("bob").build();
    when(userService.getUserByUsername("bob")).thenReturn(target);
    when(userBanRepository.existsByBannerIdAndBannedId(eq(meId), eq(otherId))).thenReturn(false);
    when(userBanRepository.existsByBannerIdAndBannedId(eq(otherId), eq(meId))).thenReturn(true);

    assertThrows(IllegalArgumentException.class, () -> service.sendRequest(meId, "bob"));
  }

  @Test
  void accept_onlyAddresseeCanAccept() {
    UUID requestId = create(UUID.class);
    Friendship pending =
        Friendship.builder()
            .id(requestId)
            .requesterId(otherId)
            .addresseeId(meId)
            .status(Friendship.STATUS_PENDING)
            .build();
    when(friendshipRepository.findById(requestId)).thenReturn(Optional.of(pending));
    when(friendshipRepository.save(any(Friendship.class)))
        .thenAnswer(inv -> inv.getArgument(0, Friendship.class));

    Friendship result = service.accept(meId, requestId);

    assertEquals(Friendship.STATUS_ACCEPTED, result.getStatus());
    verify(eventPublisher).publishAccepted(otherId, meId, result);
  }

  @Test
  void accept_rejectsWhenRequesterTriesToAccept() {
    UUID requestId = create(UUID.class);
    Friendship pending =
        Friendship.builder()
            .id(requestId)
            .requesterId(meId)
            .addresseeId(otherId)
            .status(Friendship.STATUS_PENDING)
            .build();
    when(friendshipRepository.findById(requestId)).thenReturn(Optional.of(pending));

    assertThrows(IllegalArgumentException.class, () -> service.accept(meId, requestId));
  }

  @Test
  void reject_deletesPendingRow() {
    UUID requestId = create(UUID.class);
    Friendship pending =
        Friendship.builder()
            .id(requestId)
            .requesterId(otherId)
            .addresseeId(meId)
            .status(Friendship.STATUS_PENDING)
            .build();
    when(friendshipRepository.findById(requestId)).thenReturn(Optional.of(pending));

    service.reject(meId, requestId);

    verify(friendshipRepository).delete(pending);
  }

  @Test
  void listAcceptedFriends_returnsBothDirections() {
    when(friendshipRepository.findAcceptedForUser(meId))
        .thenReturn(
            List.of(
                Friendship.builder()
                    .requesterId(meId)
                    .addresseeId(otherId)
                    .status(Friendship.STATUS_ACCEPTED)
                    .build(),
                Friendship.builder()
                    .requesterId(UUID.randomUUID())
                    .addresseeId(meId)
                    .status(Friendship.STATUS_ACCEPTED)
                    .build()));

    List<Friendship> friends = service.listAccepted(meId);

    assertEquals(2, friends.size());
  }

  @Test
  void removeFriend_deletesAcceptedRowAndPublishesEvent() {
    Friendship accepted =
        Friendship.builder()
            .id(create(UUID.class))
            .requesterId(meId)
            .addresseeId(otherId)
            .status(Friendship.STATUS_ACCEPTED)
            .build();
    when(friendshipRepository.findBetween(meId, otherId)).thenReturn(Optional.of(accepted));

    service.removeFriend(meId, otherId);

    verify(friendshipRepository).delete(accepted);
    verify(eventPublisher).publishRemoved(otherId, meId);
  }
}
