package com.hackathon.features.dms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.hackathon.features.bans.UserBanRepository;
import com.hackathon.features.friendships.Friendship;
import com.hackathon.features.friendships.FriendshipRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;

class DirectMessageServiceTest {
  @Mock DirectMessageRepository directMessageRepository;
  @Mock DirectConversationRepository directConversationRepository;
  @Mock ConversationService conversationService;
  @Mock FriendshipRepository friendshipRepository;
  @Mock UserBanRepository userBanRepository;

  DirectMessageService service;
  UUID meId;
  UUID otherId;
  UUID conversationId;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service =
        new DirectMessageService(
            directMessageRepository,
            directConversationRepository,
            conversationService,
            friendshipRepository,
            userBanRepository);
    meId = UUID.randomUUID();
    otherId = UUID.randomUUID();
    conversationId = UUID.randomUUID();
  }

  @Test
  void send_persistsWhenFriendsAndNotBanned() {
    DirectConversation conv =
        DirectConversation.builder().id(conversationId).user1Id(meId).user2Id(otherId).build();
    when(directConversationRepository.findById(conversationId)).thenReturn(Optional.of(conv));
    when(conversationService.otherParticipant(conv, meId)).thenReturn(otherId);
    when(friendshipRepository.findBetween(meId, otherId))
        .thenReturn(Optional.of(Friendship.builder().status(Friendship.STATUS_ACCEPTED).build()));
    when(userBanRepository.existsByBannerIdAndBannedId(any(), any())).thenReturn(false);
    when(directMessageRepository.save(any(DirectMessage.class)))
        .thenAnswer(inv -> inv.getArgument(0, DirectMessage.class));

    DirectMessage saved = service.send(meId, conversationId, "hello");

    assertEquals("hello", saved.getText());
    assertEquals(meId, saved.getSenderId());
    assertEquals(conversationId, saved.getConversationId());
  }

  @Test
  void send_rejectsNonParticipant() {
    DirectConversation conv =
        DirectConversation.builder()
            .id(conversationId)
            .user1Id(UUID.randomUUID())
            .user2Id(UUID.randomUUID())
            .build();
    when(directConversationRepository.findById(conversationId)).thenReturn(Optional.of(conv));

    assertThrows(IllegalArgumentException.class, () -> service.send(meId, conversationId, "hi"));
  }

  @Test
  void send_rejectsWhenNotFriends() {
    DirectConversation conv =
        DirectConversation.builder().id(conversationId).user1Id(meId).user2Id(otherId).build();
    when(directConversationRepository.findById(conversationId)).thenReturn(Optional.of(conv));
    when(conversationService.otherParticipant(conv, meId)).thenReturn(otherId);
    when(friendshipRepository.findBetween(meId, otherId)).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> service.send(meId, conversationId, "hi"));
  }

  @Test
  void send_rejectsWhenBanEitherDirection() {
    DirectConversation conv =
        DirectConversation.builder().id(conversationId).user1Id(meId).user2Id(otherId).build();
    when(directConversationRepository.findById(conversationId)).thenReturn(Optional.of(conv));
    when(conversationService.otherParticipant(conv, meId)).thenReturn(otherId);
    when(friendshipRepository.findBetween(meId, otherId))
        .thenReturn(Optional.of(Friendship.builder().status(Friendship.STATUS_ACCEPTED).build()));
    when(userBanRepository.existsByBannerIdAndBannedId(meId, otherId)).thenReturn(false);
    when(userBanRepository.existsByBannerIdAndBannedId(otherId, meId)).thenReturn(true);

    assertThrows(IllegalArgumentException.class, () -> service.send(meId, conversationId, "hi"));
  }

  @Test
  void send_rejectsEmptyText() {
    DirectConversation conv =
        DirectConversation.builder().id(conversationId).user1Id(meId).user2Id(otherId).build();
    when(directConversationRepository.findById(conversationId)).thenReturn(Optional.of(conv));
    when(conversationService.otherParticipant(conv, meId)).thenReturn(otherId);
    when(friendshipRepository.findBetween(meId, otherId))
        .thenReturn(Optional.of(Friendship.builder().status(Friendship.STATUS_ACCEPTED).build()));

    assertThrows(IllegalArgumentException.class, () -> service.send(meId, conversationId, "  "));
  }

  @Test
  void send_rejectsTextOver3072() {
    DirectConversation conv =
        DirectConversation.builder().id(conversationId).user1Id(meId).user2Id(otherId).build();
    when(directConversationRepository.findById(conversationId)).thenReturn(Optional.of(conv));
    when(conversationService.otherParticipant(conv, meId)).thenReturn(otherId);
    when(friendshipRepository.findBetween(meId, otherId))
        .thenReturn(Optional.of(Friendship.builder().status(Friendship.STATUS_ACCEPTED).build()));

    assertThrows(
        IllegalArgumentException.class,
        () -> service.send(meId, conversationId, "x".repeat(3073)));
  }

  @Test
  void getHistory_withoutCursor_usesOrderByCreatedAt() {
    when(directMessageRepository.findByConversationIdOrderByCreatedAtDesc(
            conversationId, PageRequest.of(0, 50)))
        .thenReturn(List.of(DirectMessage.builder().text("a").build()));

    List<DirectMessage> result = service.getHistory(conversationId, null, 50);

    assertEquals(1, result.size());
  }

  @Test
  void getHistory_withCursor_usesBeforeCursor() {
    UUID beforeId = UUID.randomUUID();
    when(directMessageRepository.findByConversationIdBeforeCursor(
            conversationId, beforeId, PageRequest.of(0, 50)))
        .thenReturn(List.of());

    List<DirectMessage> result = service.getHistory(conversationId, beforeId, 50);

    assertEquals(0, result.size());
  }
}
