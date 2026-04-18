package com.hackathon.features.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.hackathon.features.rooms.ChatRoom;
import com.hackathon.features.rooms.ChatRoomService;
import com.hackathon.features.rooms.RoomInvitation;
import com.hackathon.features.rooms.RoomInvitationService;
import com.hackathon.features.rooms.RoomMemberService;
import com.hackathon.features.rooms.RoomModerationService;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RoomModerationFlowIntegrationTest {
  @Autowired UserService userService;
  @Autowired ChatRoomService chatRoomService;
  @Autowired RoomMemberService roomMemberService;
  @Autowired RoomInvitationService roomInvitationService;
  @Autowired RoomModerationService roomModerationService;

  private User register(String suffix) {
    long t = System.nanoTime();
    return userService.registerUser(
        "u" + t + "-" + suffix + "@example.com",
        "user" + t + suffix,
        "password12345");
  }

  @Test
  void fullLifecycle() {
    User owner = register("owner");
    User bob = register("bob");
    User carol = register("carol");

    // Owner creates a private room
    ChatRoom room =
        chatRoomService.createRoom("secret-" + System.nanoTime(), null, owner.getId(), "private");
    assertEquals("private", room.getVisibility());
    assertTrue(roomMemberService.isOwner(room.getId(), owner.getId()));
    assertTrue(roomMemberService.isAdmin(room.getId(), owner.getId()));

    // Owner invites Bob
    RoomInvitation invBob =
        roomInvitationService.invite(room.getId(), owner.getId(), bob.getUsername());
    assertEquals(1, roomInvitationService.listMyIncoming(bob.getId()).size());

    // Bob accepts
    roomInvitationService.accept(invBob.getId(), bob.getId());
    assertTrue(roomMemberService.isMember(room.getId(), bob.getId()));
    assertEquals(0, roomInvitationService.listMyIncoming(bob.getId()).size());

    // Bob invites Carol (must be a member to invite)
    RoomInvitation invCarol =
        roomInvitationService.invite(room.getId(), bob.getId(), carol.getUsername());
    roomInvitationService.accept(invCarol.getId(), carol.getId());

    // Owner promotes Bob to admin
    roomModerationService.promoteAdmin(room.getId(), owner.getId(), bob.getId());
    assertTrue(roomMemberService.isAdmin(room.getId(), bob.getId()));

    // Bob (admin) kicks Carol
    roomModerationService.kick(room.getId(), bob.getId(), carol.getId());
    assertFalse(roomMemberService.isMember(room.getId(), carol.getId()));
    // Carol is now in the ban list; she cannot be invited again
    assertThrows(
        IllegalArgumentException.class,
        () -> roomInvitationService.invite(room.getId(), owner.getId(), carol.getUsername()));

    // Owner unbans Carol
    roomModerationService.unban(room.getId(), owner.getId(), carol.getId());
    // Now she can be invited again
    RoomInvitation invCarol2 =
        roomInvitationService.invite(room.getId(), owner.getId(), carol.getUsername());
    roomInvitationService.accept(invCarol2.getId(), carol.getId());

    // Owner demotes Bob — Bob must not be able to kick anyone now
    roomModerationService.demoteAdmin(room.getId(), owner.getId(), bob.getId());
    assertFalse(roomMemberService.isAdmin(room.getId(), bob.getId()));
    assertThrows(
        IllegalArgumentException.class,
        () -> roomModerationService.kick(room.getId(), bob.getId(), carol.getId()));

    // Owner deletes the room — cascades to members/invitations/bans
    chatRoomService.deleteRoom(room.getId(), owner.getId());
    assertFalse(roomMemberService.isMember(room.getId(), owner.getId()));
    assertFalse(roomMemberService.isMember(room.getId(), bob.getId()));
    assertFalse(roomMemberService.isMember(room.getId(), carol.getId()));
  }

  @Test
  void cannotJoinPrivateRoomWithoutInvitation() {
    User owner = register("a");
    User outsider = register("b");
    ChatRoom room =
        chatRoomService.createRoom(
            "closed-" + System.nanoTime(), null, owner.getId(), "private");

    assertThrows(
        IllegalArgumentException.class,
        () -> chatRoomService.joinRoom(room.getId(), outsider.getId()));
  }

  @Test
  void bannedUserCannotJoinPublicRoom() {
    User owner = register("a");
    User troll = register("b");
    ChatRoom room =
        chatRoomService.createRoom(
            "public-" + System.nanoTime(), null, owner.getId(), "public");
    // Troll joins, owner kicks (= bans)
    chatRoomService.joinRoom(room.getId(), troll.getId());
    roomModerationService.kick(room.getId(), owner.getId(), troll.getId());

    assertThrows(
        IllegalArgumentException.class,
        () -> chatRoomService.joinRoom(room.getId(), troll.getId()));
  }
}
