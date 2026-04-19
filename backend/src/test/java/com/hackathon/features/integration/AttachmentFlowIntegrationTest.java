package com.hackathon.features.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.hackathon.features.dms.ConversationService;
import com.hackathon.features.dms.DirectConversation;
import com.hackathon.features.dms.DirectMessage;
import com.hackathon.features.dms.DirectMessageAttachmentRepository;
import com.hackathon.features.dms.DirectMessageService;
import com.hackathon.features.friendships.Friendship;
import com.hackathon.features.friendships.FriendshipService;
import com.hackathon.features.messages.Message;
import com.hackathon.features.messages.MessageAttachmentRepository;
import com.hackathon.features.messages.MessageService;
import com.hackathon.features.rooms.ChatRoom;
import com.hackathon.features.rooms.ChatRoomRepository;
import com.hackathon.features.rooms.ChatRoomService;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.dto.ChatMessageDTO;
import com.hackathon.shared.dto.DirectMessageDTO;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AttachmentFlowIntegrationTest {
  @Autowired UserService userService;
  @Autowired ChatRoomService chatRoomService;
  @Autowired ChatRoomRepository chatRoomRepository;
  @Autowired MessageService messageService;
  @Autowired MessageAttachmentRepository messageAttachmentRepository;
  @Autowired DirectMessageService directMessageService;
  @Autowired DirectMessageAttachmentRepository directMessageAttachmentRepository;
  @Autowired ConversationService conversationService;
  @Autowired FriendshipService friendshipService;

  private User register(String s) {
    long t = System.nanoTime();
    return userService.registerUser(
        "u" + t + "-" + s + "@example.com",
        "user" + t + s,
        "password12345");
  }

  private void makeFriends(User a, User b) {
    Friendship req = friendshipService.sendRequest(a.getId(), b.getUsername());
    friendshipService.accept(b.getId(), req.getId());
  }

  @Test
  void roomAttachment_softDelete_removesRow() {
    User alice = register("a");
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, alice.getId(), "public");
    byte[] bytes = "image-bytes".getBytes();
    Message m = messageService.sendMessage(room.getId(), alice.getId(), null, null,
        "pic.png", "image/png", bytes.length, new ByteArrayInputStream(bytes));
    ChatMessageDTO dto = messageService.toDto(m);
    assertNotNull(dto.getAttachment());
    assertTrue(messageAttachmentRepository.findById(dto.getAttachment().id()).isPresent());

    messageService.deleteMessage(m.getId(), alice.getId());
    assertTrue(messageAttachmentRepository.findById(dto.getAttachment().id()).isEmpty());
  }

  @Test
  void roomAttachment_hardCascadeOnRoomDelete_removesAttachmentRow() {
    User alice = register("a");
    ChatRoom room = chatRoomService.createRoom("r-" + System.nanoTime(), null, alice.getId(), "public");
    byte[] bytes = "bytes".getBytes();
    Message m = messageService.sendMessage(room.getId(), alice.getId(), null, null,
        "x.txt", "text/plain", bytes.length, new ByteArrayInputStream(bytes));
    ChatMessageDTO dto = messageService.toDto(m);
    assertTrue(messageAttachmentRepository.findById(dto.getAttachment().id()).isPresent());

    chatRoomService.deleteRoom(room.getId(), alice.getId());
    assertTrue(messageAttachmentRepository.findById(dto.getAttachment().id()).isEmpty());
  }

  @Test
  void dmAttachment_softDelete_removesRow() {
    User a = register("a");
    User b = register("b");
    makeFriends(a, b);
    DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());
    byte[] bytes = "data".getBytes();
    DirectMessage m = directMessageService.send(a.getId(), conv.getId(), null, null,
        "doc.pdf", "application/pdf", bytes.length, new ByteArrayInputStream(bytes));
    DirectMessageDTO dto = directMessageService.toDto(m);
    assertNotNull(dto.getAttachment());

    directMessageService.deleteMessage(m.getId(), a.getId());
    assertTrue(directMessageAttachmentRepository.findById(dto.getAttachment().id()).isEmpty());
  }
}
