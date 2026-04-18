package com.hackathon.features.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.hackathon.features.dms.ConversationService;
import com.hackathon.features.dms.DirectConversation;
import com.hackathon.features.dms.DirectMessage;
import com.hackathon.features.dms.DirectMessageService;
import com.hackathon.features.friendships.Friendship;
import com.hackathon.features.friendships.FriendshipService;
import com.hackathon.features.messages.Message;
import com.hackathon.features.messages.MessageService;
import com.hackathon.features.rooms.ChatRoom;
import com.hackathon.features.rooms.ChatRoomService;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.dto.ChatMessageDTO;
import com.hackathon.shared.dto.DirectMessageDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MessageContentFlowIntegrationTest {
    @Autowired UserService userService;
    @Autowired ChatRoomService chatRoomService;
    @Autowired MessageService messageService;
    @Autowired DirectMessageService directMessageService;
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
    void room_send_reply_edit_delete_flow() {
        User author = register("author");
        User peer = register("peer");
        ChatRoom room = chatRoomService.createRoom(
                "flow-" + System.nanoTime(), null, author.getId(), "public");
        chatRoomService.joinRoom(room.getId(), peer.getId());

        Message parent = messageService.sendMessage(room.getId(), author.getId(), "original");
        Message reply = messageService.sendMessage(room.getId(), peer.getId(), "re: original", parent.getId());
        ChatMessageDTO replyDto = messageService.toDto(reply);
        assertNotNull(replyDto.getReplyTo());
        assertEquals("original", replyDto.getReplyTo().textPreview());

        Message edited = messageService.editMessage(reply.getId(), peer.getId(), "re: (edited)");
        assertEquals("re: (edited)", edited.getText());
        assertNotNull(edited.getEditedAt());

        messageService.deleteMessage(parent.getId(), author.getId());
        ChatMessageDTO parentDtoAfter = messageService.toDto(
                messageService.getMessageHistory(room.getId(), null, 50).stream()
                        .filter(m -> m.getId().equals(parent.getId()))
                        .findFirst().orElseThrow());
        assertNull(parentDtoAfter.getText());
        assertNotNull(parentDtoAfter.getDeletedAt());

        ChatMessageDTO replyDtoAfterParentDelete = messageService.toDto(
                messageService.getMessageHistory(room.getId(), null, 50).stream()
                        .filter(m -> m.getId().equals(reply.getId()))
                        .findFirst().orElseThrow());
        assertEquals("[deleted]", replyDtoAfterParentDelete.getReplyTo().textPreview());
    }

    @Test
    void dm_send_reply_edit_delete_flow() {
        User a = register("a");
        User b = register("b");
        makeFriends(a, b);
        DirectConversation conv = conversationService.getOrCreate(a.getId(), b.getId());

        DirectMessage parent = directMessageService.send(a.getId(), conv.getId(), "hello");
        DirectMessage reply = directMessageService.send(b.getId(), conv.getId(), "hi back", parent.getId());
        DirectMessageDTO replyDto = directMessageService.toDto(reply);
        assertEquals("hello", replyDto.getReplyTo().textPreview());

        DirectMessage edited = directMessageService.editMessage(reply.getId(), b.getId(), "hi back (edited)");
        assertEquals("hi back (edited)", edited.getText());
        assertNotNull(edited.getEditedAt());

        directMessageService.deleteMessage(parent.getId(), a.getId());
        DirectMessageDTO parentAfter = directMessageService.toDto(
                directMessageService.getHistory(conv.getId(), null, 50).stream()
                        .filter(m -> m.getId().equals(parent.getId()))
                        .findFirst().orElseThrow());
        assertNull(parentAfter.getText());
        assertNotNull(parentAfter.getDeletedAt());
    }
}
