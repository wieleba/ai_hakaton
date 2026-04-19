package com.hackathon.features.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.hackathon.features.dms.ConversationService;
import com.hackathon.features.dms.DirectConversation;
import com.hackathon.features.dms.DirectMessage;
import com.hackathon.features.dms.DirectMessageRepository;
import com.hackathon.features.dms.DirectMessageService;
import com.hackathon.features.friendships.Friendship;
import com.hackathon.features.friendships.FriendshipService;
import com.hackathon.features.messages.Message;
import com.hackathon.features.messages.MessageRepository;
import com.hackathon.features.messages.MessageService;
import com.hackathon.features.rooms.ChatRoom;
import com.hackathon.features.rooms.ChatRoomRepository;
import com.hackathon.features.rooms.ChatRoomService;
import com.hackathon.features.rooms.RoomMemberService;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AccountDeletionFlowIntegrationTest {
  @Autowired UserService userService;
  @Autowired ChatRoomService chatRoomService;
  @Autowired ChatRoomRepository chatRoomRepository;
  @Autowired RoomMemberService roomMemberService;
  @Autowired MessageService messageService;
  @Autowired MessageRepository messageRepository;
  @Autowired DirectMessageService directMessageService;
  @Autowired DirectMessageRepository directMessageRepository;
  @Autowired ConversationService conversationService;
  @Autowired FriendshipService friendshipService;

  private User register(String suffix) {
    long t = System.nanoTime();
    return userService.registerUser(
        "u" + t + "-" + suffix + "@example.com",
        "user" + t + suffix,
        "password12345");
  }

  private void makeFriends(User a, User b) {
    Friendship req = friendshipService.sendRequest(a.getId(), b.getUsername());
    friendshipService.accept(b.getId(), req.getId());
  }

  @Test
  void deleteAccount_cascadesOwnedRoomsAndMemberships_preservesBobsUnrelatedData() {
    User alice = register("alice");
    User bob = register("bob");
    makeFriends(alice, bob);

    // Alice owns a room; Bob joins and posts in it
    ChatRoom aliceRoom = chatRoomService.createRoom(
        "alice-" + System.nanoTime(), null, alice.getId(), "public");
    chatRoomService.joinRoom(aliceRoom.getId(), bob.getId());
    Message aliceMsg = messageService.sendMessage(aliceRoom.getId(), alice.getId(), "hi from alice");
    Message bobMsgInAliceRoom =
        messageService.sendMessage(aliceRoom.getId(), bob.getId(), "hi from bob in alice's room");

    // Bob owns his own room; Alice joins and posts
    ChatRoom bobRoom = chatRoomService.createRoom(
        "bob-" + System.nanoTime(), null, bob.getId(), "public");
    chatRoomService.joinRoom(bobRoom.getId(), alice.getId());
    Message aliceMsgInBobRoom =
        messageService.sendMessage(bobRoom.getId(), alice.getId(), "hi from alice in bob's room");

    // Alice + Bob exchange a DM
    DirectConversation conv = conversationService.getOrCreate(alice.getId(), bob.getId());
    DirectMessage dm = directMessageService.send(alice.getId(), conv.getId(), "secret");

    // Act: Alice deletes her account
    userService.deleteAccount(alice.getId());

    // Alice's owned room is gone (cascade)
    assertFalse(chatRoomRepository.existsById(aliceRoom.getId()));
    // Alice's message in her own room is gone (room cascade)
    assertFalse(messageRepository.existsById(aliceMsg.getId()));
    // Bob's message in Alice's room is also gone (the room itself is gone)
    assertFalse(messageRepository.existsById(bobMsgInAliceRoom.getId()));

    // Bob's own room survives
    assertTrue(chatRoomRepository.existsById(bobRoom.getId()));
    // Alice's message in Bob's room survives but with user_id = NULL
    Message surviving = messageRepository.findById(aliceMsgInBobRoom.getId()).orElseThrow();
    assertNull(surviving.getUserId());

    // Alice's membership in Bob's room is gone
    assertFalse(roomMemberService.isMember(bobRoom.getId(), alice.getId()));
    // Bob is still a member of his own room
    assertTrue(roomMemberService.isMember(bobRoom.getId(), bob.getId()));

    // The DM conversation (and its messages) are gone (cascade via user1/user2)
    assertFalse(directMessageRepository.existsById(dm.getId()));
  }

  @Test
  void deletedAuthor_displaysAsDeletedUserInHistory() {
    User alice = register("alice");
    User bob = register("bob");

    ChatRoom bobRoom = chatRoomService.createRoom(
        "bob-" + System.nanoTime(), null, bob.getId(), "public");
    chatRoomService.joinRoom(bobRoom.getId(), alice.getId());
    Message aliceMsg = messageService.sendMessage(bobRoom.getId(), alice.getId(), "hi");

    userService.deleteAccount(alice.getId());

    Message reloaded = messageRepository.findById(aliceMsg.getId()).orElseThrow();
    assertEquals("Deleted user", messageService.toDto(reloaded).getUsername());
  }
}
