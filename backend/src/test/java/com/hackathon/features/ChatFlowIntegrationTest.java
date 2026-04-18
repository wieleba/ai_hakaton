package com.hackathon.features;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.hackathon.features.messages.Message;
import com.hackathon.features.messages.MessageRepository;
import com.hackathon.features.messages.MessageService;
import com.hackathon.features.rooms.ChatRoom;
import com.hackathon.features.rooms.ChatRoomRepository;
import com.hackathon.features.rooms.ChatRoomService;
import com.hackathon.features.rooms.RoomBanRepository;
import com.hackathon.features.rooms.RoomMember;
import com.hackathon.features.rooms.RoomMemberRepository;
import com.hackathon.features.rooms.RoomMemberService;
import com.hackathon.features.users.UserService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * Integration test for the full chat flow: create room → join → send message → read history.
 * Uses Mockito to unit-test service interactions without a running database.
 */
@ExtendWith(MockitoExtension.class)
class ChatFlowIntegrationTest {

    // ---- Repositories (mocked) ----
    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private RoomMemberRepository roomMemberRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private UserService userService;
    @Mock private RoomBanRepository roomBanRepository;

    // ---- Services (real, wired by Mockito) ----
    @InjectMocks private RoomMemberService roomMemberService;

    private ChatRoomService chatRoomService;
    private MessageService messageService;

    private UUID ownerId;
    private UUID memberId;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        chatRoomService = new ChatRoomService(chatRoomRepository, roomMemberService, userService, roomBanRepository);
        messageService = new MessageService(messageRepository, roomMemberService);

        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        roomId = UUID.randomUUID();
    }

    // ------------------------------------------------------------------ //
    //  RoomMemberService tests                                             //
    // ------------------------------------------------------------------ //

    @Test
    void roomMemberService_addMember_savesRecord() {
        roomMemberService.addMember(roomId, ownerId);
        verify(roomMemberRepository).save(any(RoomMember.class));
    }

    @Test
    void roomMemberService_isMember_returnsTrue_whenExists() {
        when(roomMemberRepository.existsByRoomIdAndUserId(roomId, ownerId)).thenReturn(true);
        assertTrue(roomMemberService.isMember(roomId, ownerId));
    }

    @Test
    void roomMemberService_isMember_returnsFalse_whenAbsent() {
        when(roomMemberRepository.existsByRoomIdAndUserId(roomId, memberId)).thenReturn(false);
        assertFalse(roomMemberService.isMember(roomId, memberId));
    }

    @Test
    void roomMemberService_removeMember_deletesRecord() {
        roomMemberService.removeMember(roomId, memberId);
        verify(roomMemberRepository).deleteByRoomIdAndUserId(roomId, memberId);
    }

    @Test
    void roomMemberService_getMembers_returnsList() {
        RoomMember m1 = RoomMember.builder().roomId(roomId).userId(ownerId).build();
        RoomMember m2 = RoomMember.builder().roomId(roomId).userId(memberId).build();
        when(roomMemberRepository.findByRoomId(roomId)).thenReturn(List.of(m1, m2));

        List<UUID> members = roomMemberService.getMembers(roomId);
        assertEquals(2, members.size());
        assertTrue(members.contains(ownerId));
        assertTrue(members.contains(memberId));
    }

    // ------------------------------------------------------------------ //
    //  ChatRoomService tests                                               //
    // ------------------------------------------------------------------ //

    @Test
    void chatRoomService_createRoom_success() {
        when(chatRoomRepository.existsByName("general")).thenReturn(false);
        ChatRoom saved = ChatRoom.builder()
                .id(roomId)
                .name("general")
                .description("General chat")
                .ownerId(ownerId)
                .visibility("public")
                .build();
        when(chatRoomRepository.save(any(ChatRoom.class))).thenReturn(saved);

        ChatRoom result = chatRoomService.createRoom("general", "General chat", ownerId, null);

        assertNotNull(result);
        assertEquals("general", result.getName());
        assertEquals(ownerId, result.getOwnerId());
        verify(roomMemberRepository).save(any(RoomMember.class)); // owner auto-joined
    }

    @Test
    void chatRoomService_createRoom_throwsWhenNameTaken() {
        when(chatRoomRepository.existsByName("general")).thenReturn(true);
        assertThrows(
                IllegalArgumentException.class,
                () -> chatRoomService.createRoom("general", "desc", ownerId, null));
    }

    @Test
    void chatRoomService_listPublicRooms_returnsPage() {
        ChatRoom room = ChatRoom.builder()
                .id(roomId)
                .name("general")
                .visibility("public")
                .ownerId(ownerId)
                .build();
        Page<ChatRoom> page = new PageImpl<>(List.of(room));
        when(chatRoomRepository.findByVisibility(eq("public"), any(Pageable.class))).thenReturn(page);

        Page<ChatRoom> result = chatRoomService.listPublicRooms(0, 20);
        assertEquals(1, result.getTotalElements());
        assertEquals("general", result.getContent().get(0).getName());
    }

    @Test
    void chatRoomService_joinRoom_success() {
        ChatRoom room = ChatRoom.builder()
                .id(roomId)
                .name("general")
                .visibility("public")
                .ownerId(ownerId)
                .build();
        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(roomMemberRepository.existsByRoomIdAndUserId(roomId, memberId)).thenReturn(false);

        chatRoomService.joinRoom(roomId, memberId);

        verify(roomMemberRepository).save(any(RoomMember.class));
    }

    @Test
    void chatRoomService_joinRoom_isIdempotentWhenAlreadyMember() {
        // Re-joining (e.g. after a previous Leave) must be a silent no-op
        // — the frontend calls joinRoom on every ChatPage mount so users who
        // left and re-entered are automatically re-added.
        ChatRoom room = ChatRoom.builder()
                .id(roomId)
                .name("general")
                .visibility("public")
                .ownerId(ownerId)
                .build();
        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(roomMemberRepository.existsByRoomIdAndUserId(roomId, memberId)).thenReturn(true);

        chatRoomService.joinRoom(roomId, memberId);

        verify(roomMemberRepository, never()).save(any(RoomMember.class));
    }

    @Test
    void chatRoomService_leaveRoom_success() {
        ChatRoom room = ChatRoom.builder()
                .id(roomId)
                .name("general")
                .visibility("public")
                .ownerId(ownerId)
                .build();
        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

        chatRoomService.leaveRoom(roomId, memberId);

        verify(roomMemberRepository).deleteByRoomIdAndUserId(roomId, memberId);
    }

    @Test
    void chatRoomService_leaveRoom_throwsWhenOwnerTriesToLeave() {
        ChatRoom room = ChatRoom.builder()
                .id(roomId)
                .name("general")
                .visibility("public")
                .ownerId(ownerId)
                .build();
        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

        assertThrows(
                IllegalArgumentException.class,
                () -> chatRoomService.leaveRoom(roomId, ownerId));
    }

    @Test
    void chatRoomService_getRoomById_returnsRoom() {
        ChatRoom room = ChatRoom.builder().id(roomId).name("general").ownerId(ownerId).build();
        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

        ChatRoom result = chatRoomService.getRoomById(roomId);
        assertEquals(roomId, result.getId());
    }

    @Test
    void chatRoomService_getRoomById_throwsWhenNotFound() {
        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.empty());
        assertThrows(
                IllegalArgumentException.class,
                () -> chatRoomService.getRoomById(roomId));
    }

    // ------------------------------------------------------------------ //
    //  MessageService tests                                               //
    // ------------------------------------------------------------------ //

    @Test
    void messageService_sendMessage_success() {
        when(roomMemberRepository.existsByRoomIdAndUserId(roomId, ownerId)).thenReturn(true);
        Message saved = Message.builder()
                .id(UUID.randomUUID())
                .roomId(roomId)
                .userId(ownerId)
                .text("Hello world")
                .createdAt(OffsetDateTime.now())
                .build();
        when(messageRepository.save(any(Message.class))).thenReturn(saved);

        Message result = messageService.sendMessage(roomId, ownerId, "Hello world");

        assertNotNull(result);
        assertEquals("Hello world", result.getText());
        assertEquals(ownerId, result.getUserId());
    }

    @Test
    void messageService_sendMessage_throwsWhenNotMember() {
        when(roomMemberRepository.existsByRoomIdAndUserId(roomId, memberId)).thenReturn(false);
        assertThrows(
                IllegalArgumentException.class,
                () -> messageService.sendMessage(roomId, memberId, "Hello"));
    }

    @Test
    void messageService_sendMessage_throwsWhenTextEmpty() {
        when(roomMemberRepository.existsByRoomIdAndUserId(roomId, ownerId)).thenReturn(true);
        assertThrows(
                IllegalArgumentException.class,
                () -> messageService.sendMessage(roomId, ownerId, ""));
    }

    @Test
    void messageService_sendMessage_throwsWhenTextTooLong() {
        when(roomMemberRepository.existsByRoomIdAndUserId(roomId, ownerId)).thenReturn(true);
        String tooLong = "x".repeat(3073);
        assertThrows(
                IllegalArgumentException.class,
                () -> messageService.sendMessage(roomId, ownerId, tooLong));
    }

    @Test
    void messageService_getMessageHistory_withoutCursor() {
        Message m1 = Message.builder().id(UUID.randomUUID()).roomId(roomId).userId(ownerId).text("m1").build();
        Message m2 = Message.builder().id(UUID.randomUUID()).roomId(roomId).userId(ownerId).text("m2").build();
        when(messageRepository.findByRoomIdOrderByCreatedAtDesc(eq(roomId), any(Pageable.class)))
                .thenReturn(List.of(m1, m2));

        List<Message> result = messageService.getMessageHistory(roomId, null, 50);

        assertEquals(2, result.size());
    }

    @Test
    void messageService_getMessageHistory_withCursor() {
        UUID cursorId = UUID.randomUUID();
        Message m = Message.builder().id(UUID.randomUUID()).roomId(roomId).userId(ownerId).text("old").build();
        when(messageRepository.findByRoomIdBeforeCursor(eq(roomId), eq(cursorId), any(Pageable.class)))
                .thenReturn(List.of(m));

        List<Message> result = messageService.getMessageHistory(roomId, cursorId, 50);

        assertEquals(1, result.size());
        assertEquals("old", result.get(0).getText());
    }

    @Test
    void messageService_getMessageCount_returnsCount() {
        when(messageRepository.countByRoomId(roomId)).thenReturn(42L);
        assertEquals(42L, messageService.getMessageCount(roomId));
    }

    // ------------------------------------------------------------------ //
    //  Full chat flow: end-to-end scenario                                //
    // ------------------------------------------------------------------ //

    @Test
    void fullChatFlow_createRoomJoinSendReadMessages() {
        // Step 1: Owner creates a room
        when(chatRoomRepository.existsByName("flow-room")).thenReturn(false);
        ChatRoom room = ChatRoom.builder()
                .id(roomId)
                .name("flow-room")
                .description("E2E flow room")
                .ownerId(ownerId)
                .visibility("public")
                .build();
        when(chatRoomRepository.save(any(ChatRoom.class))).thenReturn(room);

        ChatRoom created = chatRoomService.createRoom("flow-room", "E2E flow room", ownerId, null);
        assertEquals("flow-room", created.getName());

        // Step 2: Second user joins the room
        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(roomMemberRepository.existsByRoomIdAndUserId(roomId, memberId)).thenReturn(false);
        chatRoomService.joinRoom(roomId, memberId);
        verify(roomMemberRepository, atLeast(2)).save(any(RoomMember.class)); // owner + member

        // Step 3: Owner sends a message
        when(roomMemberRepository.existsByRoomIdAndUserId(roomId, ownerId)).thenReturn(true);
        Message ownerMsg = Message.builder()
                .id(UUID.randomUUID())
                .roomId(roomId)
                .userId(ownerId)
                .text("Welcome everyone!")
                .build();
        when(messageRepository.save(any(Message.class))).thenReturn(ownerMsg);
        Message sent = messageService.sendMessage(roomId, ownerId, "Welcome everyone!");
        assertEquals("Welcome everyone!", sent.getText());

        // Step 4: Member sends a message
        when(roomMemberRepository.existsByRoomIdAndUserId(roomId, memberId)).thenReturn(true);
        Message memberMsg = Message.builder()
                .id(UUID.randomUUID())
                .roomId(roomId)
                .userId(memberId)
                .text("Thanks for the invite!")
                .build();
        when(messageRepository.save(any(Message.class))).thenReturn(memberMsg);
        Message memberSent = messageService.sendMessage(roomId, memberId, "Thanks for the invite!");
        assertEquals("Thanks for the invite!", memberSent.getText());

        // Step 5: Read message history
        when(messageRepository.findByRoomIdOrderByCreatedAtDesc(eq(roomId), any(Pageable.class)))
                .thenReturn(List.of(memberMsg, ownerMsg));
        List<Message> history = messageService.getMessageHistory(roomId, null, 50);
        assertEquals(2, history.size());

        // Step 6: Member leaves the room
        chatRoomService.leaveRoom(roomId, memberId);
        verify(roomMemberRepository).deleteByRoomIdAndUserId(roomId, memberId);
    }
}
