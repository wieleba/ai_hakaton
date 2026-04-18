package com.hackathon.features.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.hackathon.features.bans.UserBanService;
import com.hackathon.features.dms.ConversationService;
import com.hackathon.features.dms.DirectConversation;
import com.hackathon.features.dms.DirectMessage;
import com.hackathon.features.dms.DirectMessageService;
import com.hackathon.features.friendships.Friendship;
import com.hackathon.features.friendships.FriendshipService;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FriendsAndDmsFlowIntegrationTest {
  @Autowired UserService userService;
  @Autowired FriendshipService friendshipService;
  @Autowired UserBanService userBanService;
  @Autowired ConversationService conversationService;
  @Autowired DirectMessageService directMessageService;

  private User register(String suffix) {
    long t = System.nanoTime();
    return userService.registerUser(
        "u" + t + "-" + suffix + "@example.com",
        "user" + t + suffix,
        "password12345");
  }

  @Test
  void fullHappyPath() {
    User alice = register("alice");
    User bob = register("bob");

    // Alice sends friend request to Bob
    Friendship req = friendshipService.sendRequest(alice.getId(), bob.getUsername());
    assertEquals(Friendship.STATUS_PENDING, req.getStatus());
    assertEquals(alice.getId(), req.getRequesterId());
    assertEquals(bob.getId(), req.getAddresseeId());

    // Bob accepts
    Friendship accepted = friendshipService.accept(bob.getId(), req.getId());
    assertEquals(Friendship.STATUS_ACCEPTED, accepted.getStatus());

    // Alice sends DM to Bob
    DirectMessage dm1 =
        directMessageService.sendToUser(alice.getId(), bob.getId(), "hey bob");
    assertEquals("hey bob", dm1.getText());
    assertEquals(alice.getId(), dm1.getSenderId());

    // Conversation id must match what ConversationService returns for the pair
    DirectConversation conv = conversationService.getOrCreate(alice.getId(), bob.getId());
    assertEquals(dm1.getConversationId(), conv.getId());

    // Bob replies
    DirectMessage dm2 = directMessageService.send(bob.getId(), conv.getId(), "hey alice");
    assertEquals("hey alice", dm2.getText());
    assertEquals(bob.getId(), dm2.getSenderId());

    // History returns both (most-recent first per cursor-paginated query)
    List<DirectMessage> history = directMessageService.getHistory(conv.getId(), null, 50);
    assertEquals(2, history.size());

    // Bob bans Alice
    userBanService.ban(bob.getId(), alice.getId());

    // Alice cannot send DM now
    assertThrows(
        IllegalArgumentException.class,
        () -> directMessageService.send(alice.getId(), conv.getId(), "still there?"));

    // Friendship is gone for both
    assertTrue(friendshipService.listAccepted(alice.getId()).isEmpty());
    assertTrue(friendshipService.listAccepted(bob.getId()).isEmpty());
  }

  @Test
  void autoAcceptOnInverseRequest() {
    User a = register("a");
    User b = register("b");

    Friendship first = friendshipService.sendRequest(a.getId(), b.getUsername());
    assertEquals(Friendship.STATUS_PENDING, first.getStatus());

    // B sends the inverse request — service should auto-accept both sides
    Friendship second = friendshipService.sendRequest(b.getId(), a.getUsername());
    assertEquals(Friendship.STATUS_ACCEPTED, second.getStatus());

    // Both lists now show the friendship
    assertEquals(1, friendshipService.listAccepted(a.getId()).size());
    assertEquals(1, friendshipService.listAccepted(b.getId()).size());
  }
}
