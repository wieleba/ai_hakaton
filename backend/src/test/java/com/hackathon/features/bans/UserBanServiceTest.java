package com.hackathon.features.bans;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.hackathon.features.friendships.Friendship;
import com.hackathon.features.friendships.FriendshipRepository;
import com.hackathon.shared.websocket.FriendshipEventPublisher;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class UserBanServiceTest {
  @Mock UserBanRepository userBanRepository;
  @Mock FriendshipRepository friendshipRepository;
  @Mock FriendshipEventPublisher eventPublisher;

  UserBanService service;
  UUID meId;
  UUID otherId;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new UserBanService(userBanRepository, friendshipRepository, eventPublisher);
    meId = UUID.randomUUID();
    otherId = UUID.randomUUID();
  }

  @Test
  void ban_insertsBanAndDeletesExistingFriendship() {
    Friendship friendship = Friendship.builder()
        .requesterId(meId)
        .addresseeId(otherId)
        .status(Friendship.STATUS_ACCEPTED)
        .build();
    when(friendshipRepository.findBetween(meId, otherId)).thenReturn(Optional.of(friendship));
    when(userBanRepository.save(any(UserBan.class)))
        .thenAnswer(inv -> inv.getArgument(0, UserBan.class));

    service.ban(meId, otherId);

    verify(userBanRepository).save(any(UserBan.class));
    verify(friendshipRepository).delete(friendship);
    verify(eventPublisher).publishRemoved(otherId, meId);
  }

  @Test
  void ban_worksWhenNoFriendshipExists() {
    when(friendshipRepository.findBetween(meId, otherId)).thenReturn(Optional.empty());
    when(userBanRepository.save(any(UserBan.class)))
        .thenAnswer(inv -> inv.getArgument(0, UserBan.class));

    service.ban(meId, otherId);

    verify(userBanRepository).save(any(UserBan.class));
    verify(friendshipRepository, never()).delete(any());
  }

  @Test
  void ban_rejectsSelf() {
    assertThrows(IllegalArgumentException.class, () -> service.ban(meId, meId));
    verifyNoInteractions(userBanRepository);
  }

  @Test
  void listBans_returnsBansBelongingToUser() {
    when(userBanRepository.findByBannerId(meId))
        .thenReturn(List.of(UserBan.builder().bannerId(meId).bannedId(otherId).build()));

    List<UserBan> result = service.listBans(meId);

    assertEquals(1, result.size());
  }
}
