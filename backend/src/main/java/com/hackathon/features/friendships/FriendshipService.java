package com.hackathon.features.friendships;

import com.hackathon.features.bans.UserBanRepository;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.websocket.FriendshipEventPublisher;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FriendshipService {
  private final FriendshipRepository friendshipRepository;
  private final UserBanRepository userBanRepository;
  private final UserService userService;
  private final FriendshipEventPublisher eventPublisher;

  @Transactional
  public Friendship sendRequest(UUID requesterId, String targetUsername) {
    User target = userService.getUserByUsername(targetUsername);
    UUID targetId = target.getId();
    if (targetId.equals(requesterId)) {
      throw new IllegalArgumentException("Cannot send friend request to yourself");
    }
    if (userBanRepository.existsByBannerIdAndBannedId(requesterId, targetId)
        || userBanRepository.existsByBannerIdAndBannedId(targetId, requesterId)) {
      throw new IllegalArgumentException("Cannot send friend request");
    }

    Optional<Friendship> existing = friendshipRepository.findBetween(requesterId, targetId);
    if (existing.isPresent()) {
      Friendship f = existing.get();
      if (Friendship.STATUS_ACCEPTED.equals(f.getStatus())) {
        throw new IllegalArgumentException("Already friends");
      }
      if (f.getRequesterId().equals(targetId) && f.getAddresseeId().equals(requesterId)) {
        f.setStatus(Friendship.STATUS_ACCEPTED);
        Friendship saved = friendshipRepository.save(f);
        eventPublisher.publishAccepted(targetId, requesterId, saved);
        eventPublisher.publishAccepted(requesterId, targetId, saved);
        return saved;
      }
      throw new IllegalArgumentException("Friend request already pending");
    }

    Friendship request =
        Friendship.builder()
            .requesterId(requesterId)
            .addresseeId(targetId)
            .status(Friendship.STATUS_PENDING)
            .build();
    Friendship saved = friendshipRepository.save(request);
    eventPublisher.publishRequestCreated(targetId, saved);
    return saved;
  }

  @Transactional
  public Friendship accept(UUID currentUserId, UUID requestId) {
    Friendship request =
        friendshipRepository
            .findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Request not found"));
    if (!request.getAddresseeId().equals(currentUserId)) {
      throw new IllegalArgumentException("Only the addressee can accept");
    }
    if (!Friendship.STATUS_PENDING.equals(request.getStatus())) {
      throw new IllegalArgumentException("Request is not pending");
    }
    request.setStatus(Friendship.STATUS_ACCEPTED);
    Friendship saved = friendshipRepository.save(request);
    eventPublisher.publishAccepted(request.getRequesterId(), currentUserId, saved);
    return saved;
  }

  @Transactional
  public void reject(UUID currentUserId, UUID requestId) {
    Friendship request =
        friendshipRepository
            .findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Request not found"));
    if (!request.getAddresseeId().equals(currentUserId)) {
      throw new IllegalArgumentException("Only the addressee can reject");
    }
    if (!Friendship.STATUS_PENDING.equals(request.getStatus())) {
      throw new IllegalArgumentException("Request is not pending");
    }
    friendshipRepository.delete(request);
  }

  @Transactional
  public void cancel(UUID currentUserId, UUID requestId) {
    Friendship request =
        friendshipRepository
            .findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Request not found"));
    if (!request.getRequesterId().equals(currentUserId)) {
      throw new IllegalArgumentException("Only the requester can cancel");
    }
    if (!Friendship.STATUS_PENDING.equals(request.getStatus())) {
      throw new IllegalArgumentException("Request is not pending");
    }
    friendshipRepository.delete(request);
  }

  public List<Friendship> listAccepted(UUID userId) {
    return friendshipRepository.findAcceptedForUser(userId);
  }

  public List<Friendship> listPendingIncoming(UUID userId) {
    return friendshipRepository.findByAddresseeIdAndStatus(userId, Friendship.STATUS_PENDING);
  }

  public List<Friendship> listPendingOutgoing(UUID userId) {
    return friendshipRepository.findByRequesterIdAndStatus(userId, Friendship.STATUS_PENDING);
  }

  @Transactional
  public void removeFriend(UUID currentUserId, UUID otherUserId) {
    Friendship friendship =
        friendshipRepository
            .findBetween(currentUserId, otherUserId)
            .filter(f -> Friendship.STATUS_ACCEPTED.equals(f.getStatus()))
            .orElseThrow(() -> new IllegalArgumentException("Not friends"));
    friendshipRepository.delete(friendship);
    eventPublisher.publishRemoved(otherUserId, currentUserId);
  }
}
