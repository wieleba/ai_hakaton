package com.hackathon.features.bans;

import com.hackathon.features.friendships.Friendship;
import com.hackathon.features.friendships.FriendshipRepository;
import com.hackathon.shared.websocket.FriendshipEventPublisher;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserBanService {
  private final UserBanRepository userBanRepository;
  private final FriendshipRepository friendshipRepository;
  private final FriendshipEventPublisher eventPublisher;

  @Transactional
  public UserBan ban(UUID bannerId, UUID bannedId) {
    if (bannerId.equals(bannedId)) {
      throw new IllegalArgumentException("Cannot ban yourself");
    }
    UserBan ban = UserBan.builder().bannerId(bannerId).bannedId(bannedId).build();
    UserBan saved = userBanRepository.save(ban);

    Optional<Friendship> friendship = friendshipRepository.findBetween(bannerId, bannedId);
    if (friendship.isPresent()) {
      friendshipRepository.delete(friendship.get());
      eventPublisher.publishRemoved(bannedId, bannerId);
    }
    return saved;
  }

  public List<UserBan> listBans(UUID bannerId) {
    return userBanRepository.findByBannerId(bannerId);
  }
}
