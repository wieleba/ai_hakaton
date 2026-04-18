import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useRoom } from '../hooks/useRoom';
import { useRoomMessages } from '../hooks/useRoomMessages';
import type { ChatRoom, Message } from '../types/room';
import * as roomServiceModule from '../services/roomService';
import * as messageServiceModule from '../services/messageService';

vi.mock('../services/roomService');
vi.mock('../services/messageService');

const mockRoom: ChatRoom = {
  id: 'room-uuid-1',
  name: 'general',
  description: 'General chat',
  ownerId: 'owner-uuid-1',
  visibility: 'public',
  createdAt: '2026-04-18T12:00:00Z',
  updatedAt: '2026-04-18T12:00:00Z',
};

const makeMessage = (id: string, text: string, offset = 0): Message => ({
  id,
  roomId: mockRoom.id,
  userId: 'user-uuid-1',
  username: 'testuser',
  text,
  createdAt: new Date(Date.now() - offset * 1000).toISOString(),
});

describe('ChatFlow Integration Tests', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // ------------------------------------------------------------------ //
  //  Room list / creation tests                                          //
  // ------------------------------------------------------------------ //

  describe('Room listing', () => {
    it('should list public rooms successfully', async () => {
      vi.mocked(roomServiceModule.roomService.listPublicRooms).mockResolvedValueOnce({
        content: [mockRoom],
        totalElements: 1,
        totalPages: 1,
      });

      const result = await roomServiceModule.roomService.listPublicRooms(0, 20);

      expect(result.content).toHaveLength(1);
      expect(result.content[0].name).toBe('general');
      expect(result.totalElements).toBe(1);
    });

    it('should return empty list when no rooms exist', async () => {
      vi.mocked(roomServiceModule.roomService.listPublicRooms).mockResolvedValueOnce({
        content: [],
        totalElements: 0,
        totalPages: 0,
      });

      const result = await roomServiceModule.roomService.listPublicRooms(0, 20);

      expect(result.content).toHaveLength(0);
      expect(result.totalElements).toBe(0);
    });

    it('should create a new room', async () => {
      vi.mocked(roomServiceModule.roomService.createRoom).mockResolvedValueOnce(mockRoom);

      const created = await roomServiceModule.roomService.createRoom('general', 'General chat');

      expect(created.id).toBe('room-uuid-1');
      expect(created.name).toBe('general');
      expect(created.visibility).toBe('public');
    });
  });

  // ------------------------------------------------------------------ //
  //  useRoom hook tests                                                  //
  // ------------------------------------------------------------------ //

  describe('useRoom hook', () => {
    it('should fetch room details', async () => {
      vi.mocked(roomServiceModule.roomService.getRoomById).mockResolvedValueOnce(mockRoom);

      const { result } = renderHook(() => useRoom());

      await act(async () => {
        await result.current.fetchRoom('room-uuid-1');
      });

      expect(result.current.currentRoom).not.toBeNull();
      expect(result.current.currentRoom?.name).toBe('general');
      expect(result.current.error).toBeNull();
    });

    it('should join a room and update current room', async () => {
      vi.mocked(roomServiceModule.roomService.joinRoom).mockResolvedValueOnce(undefined);
      vi.mocked(roomServiceModule.roomService.getRoomById).mockResolvedValueOnce(mockRoom);

      const { result } = renderHook(() => useRoom());

      await act(async () => {
        await result.current.joinRoom('room-uuid-1');
      });

      expect(result.current.currentRoom?.id).toBe('room-uuid-1');
      expect(result.current.error).toBeNull();
    });

    it('should leave a room and clear current room', async () => {
      vi.mocked(roomServiceModule.roomService.getRoomById).mockResolvedValueOnce(mockRoom);
      vi.mocked(roomServiceModule.roomService.leaveRoom).mockResolvedValueOnce(undefined);

      const { result } = renderHook(() => useRoom());

      // First join
      await act(async () => {
        await result.current.fetchRoom('room-uuid-1');
      });
      expect(result.current.currentRoom).not.toBeNull();

      // Then leave
      await act(async () => {
        await result.current.leaveRoom('room-uuid-1');
      });
      expect(result.current.currentRoom).toBeNull();
    });

    it('should set error when fetch fails', async () => {
      vi.mocked(roomServiceModule.roomService.getRoomById).mockRejectedValueOnce(
        new Error('Room not found')
      );

      const { result } = renderHook(() => useRoom());

      await act(async () => {
        await result.current.fetchRoom('nonexistent-id');
      });

      expect(result.current.currentRoom).toBeNull();
      expect(result.current.error).toBe('Room not found');
    });
  });

  // ------------------------------------------------------------------ //
  //  useRoomMessages hook tests                                          //
  // ------------------------------------------------------------------ //

  describe('useRoomMessages hook', () => {
    it('should load initial 50 messages', async () => {
      const messages = Array.from({ length: 50 }, (_, i) =>
        makeMessage(`msg-${i}`, `Message ${i}`, i)
      );
      vi.mocked(messageServiceModule.messageService.getMessageHistory).mockResolvedValueOnce(
        messages
      );

      const { result } = renderHook(() => useRoomMessages('room-uuid-1'));

      await act(async () => {
        await result.current.loadInitialMessages('room-uuid-1');
      });

      expect(result.current.messages).toHaveLength(50);
      expect(result.current.hasMore).toBe(true);
      expect(result.current.error).toBeNull();
    });

    it('should set hasMore to false when fewer than 50 messages returned', async () => {
      const messages = Array.from({ length: 30 }, (_, i) =>
        makeMessage(`msg-${i}`, `Message ${i}`, i)
      );
      vi.mocked(messageServiceModule.messageService.getMessageHistory).mockResolvedValueOnce(
        messages
      );

      const { result } = renderHook(() => useRoomMessages('room-uuid-1'));

      await act(async () => {
        await result.current.loadInitialMessages('room-uuid-1');
      });

      expect(result.current.messages).toHaveLength(30);
      expect(result.current.hasMore).toBe(false);
    });

    it('should load more messages and prepend to list', async () => {
      const initialMessages = Array.from({ length: 50 }, (_, i) =>
        makeMessage(`msg-new-${i}`, `New message ${i}`, i)
      );
      const olderMessages = Array.from({ length: 50 }, (_, i) =>
        makeMessage(`msg-old-${i}`, `Old message ${i}`, 50 + i)
      );

      vi.mocked(messageServiceModule.messageService.getMessageHistory)
        .mockResolvedValueOnce(initialMessages)
        .mockResolvedValueOnce(olderMessages);

      const { result } = renderHook(() => useRoomMessages('room-uuid-1'));

      await act(async () => {
        await result.current.loadInitialMessages('room-uuid-1');
      });
      expect(result.current.messages).toHaveLength(50);

      await act(async () => {
        await result.current.loadMoreMessages();
      });
      expect(result.current.messages).toHaveLength(100);
    });

    it('should add a new message (WebSocket push)', () => {
      const { result } = renderHook(() => useRoomMessages('room-uuid-1'));

      const newMessage = makeMessage('ws-msg-1', 'Real-time message');

      act(() => {
        result.current.addMessage(newMessage);
      });

      expect(result.current.messages).toHaveLength(1);
      expect(result.current.messages[0].text).toBe('Real-time message');
    });

    it('should clear messages and reset hasMore', () => {
      const { result } = renderHook(() => useRoomMessages('room-uuid-1'));

      act(() => {
        result.current.addMessage(makeMessage('m1', 'Hello'));
        result.current.addMessage(makeMessage('m2', 'World'));
      });
      expect(result.current.messages).toHaveLength(2);

      act(() => {
        result.current.clearMessages();
      });
      expect(result.current.messages).toHaveLength(0);
      expect(result.current.hasMore).toBe(true);
    });

    it('should set error when loading messages fails', async () => {
      vi.mocked(messageServiceModule.messageService.getMessageHistory).mockRejectedValueOnce(
        new Error('Network error')
      );

      const { result } = renderHook(() => useRoomMessages('room-uuid-1'));

      await act(async () => {
        await result.current.loadInitialMessages('room-uuid-1');
      });

      expect(result.current.error).toBe('Network error');
      expect(result.current.messages).toHaveLength(0);
    });
  });

  // ------------------------------------------------------------------ //
  //  Message service direct tests                                        //
  // ------------------------------------------------------------------ //

  describe('messageService', () => {
    it('should get message history', async () => {
      const messages = [makeMessage('m1', 'Hello'), makeMessage('m2', 'World')];
      vi.mocked(messageServiceModule.messageService.getMessageHistory).mockResolvedValueOnce(
        messages
      );

      const result = await messageServiceModule.messageService.getMessageHistory('room-uuid-1');

      expect(result).toHaveLength(2);
    });

    it('should send a message', async () => {
      const sent = makeMessage('new-id', 'Hello world');
      vi.mocked(messageServiceModule.messageService.sendMessage).mockResolvedValueOnce(sent);

      const result = await messageServiceModule.messageService.sendMessage(
        'room-uuid-1',
        'Hello world'
      );

      expect(result.text).toBe('Hello world');
      expect(result.roomId).toBe('room-uuid-1');
    });
  });

  // ------------------------------------------------------------------ //
  //  Full end-to-end chat flow scenario                                  //
  // ------------------------------------------------------------------ //

  describe('Full chat flow end-to-end', () => {
    it('should complete a full room lifecycle: create → join → send → read → leave', async () => {
      // Step 1: Create room
      vi.mocked(roomServiceModule.roomService.createRoom).mockResolvedValueOnce(mockRoom);
      const created = await roomServiceModule.roomService.createRoom('general', 'General chat');
      expect(created.name).toBe('general');

      // Step 2: Fetch room details
      vi.mocked(roomServiceModule.roomService.getRoomById).mockResolvedValueOnce(created);
      const { result: roomHook } = renderHook(() => useRoom());
      await act(async () => {
        await roomHook.current.fetchRoom(created.id);
      });
      expect(roomHook.current.currentRoom?.id).toBe(created.id);

      // Step 3: Load initial messages (empty room)
      vi.mocked(messageServiceModule.messageService.getMessageHistory).mockResolvedValueOnce([]);
      const { result: msgHook } = renderHook(() => useRoomMessages(created.id));
      await act(async () => {
        await msgHook.current.loadInitialMessages(created.id);
      });
      expect(msgHook.current.messages).toHaveLength(0);

      // Step 4: Receive a real-time message via WebSocket
      const wsMsg = makeMessage('ws-1', 'First message!');
      act(() => {
        msgHook.current.addMessage(wsMsg);
      });
      expect(msgHook.current.messages).toHaveLength(1);
      expect(msgHook.current.messages[0].text).toBe('First message!');

      // Step 5: Send a message via REST
      const sentMsg = makeMessage('sent-1', 'Reply here');
      vi.mocked(messageServiceModule.messageService.sendMessage).mockResolvedValueOnce(sentMsg);
      const sent = await messageServiceModule.messageService.sendMessage(created.id, 'Reply here');
      act(() => {
        msgHook.current.addMessage(sent);
      });
      expect(msgHook.current.messages).toHaveLength(2);

      // Step 6: Leave the room
      vi.mocked(roomServiceModule.roomService.leaveRoom).mockResolvedValueOnce(undefined);
      await act(async () => {
        await roomHook.current.leaveRoom(created.id);
      });
      expect(roomHook.current.currentRoom).toBeNull();
    });
  });
});
