import { useState, useCallback } from 'react';
import { Message } from '../types/room';
import { messageService } from '../services/messageService';
import type { RoomMessageEvent } from './useWebSocket';

export const useRoomMessages = (roomId?: string) => {
  const [messages, setMessages] = useState<Message[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadInitialMessages = useCallback(async (id: string) => {
    setIsLoading(true);
    setError(null);
    try {
      const history = await messageService.getHistory(id, undefined, 50);
      setMessages(history);
      setHasMore(history.length === 50);
    } catch (err: any) {
      setError(err.message);
    } finally {
      setIsLoading(false);
    }
  }, []);

  const loadMoreMessages = useCallback(
    async (beforeMessageId?: string) => {
      if (!roomId || !hasMore) return;
      setIsLoading(true);
      try {
        const oldestMessage = messages[messages.length - 1];
        const history = await messageService.getHistory(roomId, oldestMessage?.id, 50);
        setMessages((prev) => [...prev, ...history]);
        setHasMore(history.length === 50);
      } catch (err: any) {
        setError(err.message);
      } finally {
        setIsLoading(false);
      }
    },
    [roomId, messages, hasMore]
  );

  const addMessage = useCallback((message: Message) => {
    setMessages((prev) => [message, ...prev]);
  }, []);

  const upsertMessage = useCallback((m: Message) => {
    setMessages((prev) => prev.map((p) => (p.id === m.id ? { ...p, ...m } : p)));
  }, []);

  const markDeleted = useCallback((id: string, deletedAt: string, deletedBy: string) => {
    setMessages((prev) =>
      prev.map((p) => (p.id === id ? { ...p, text: null, deletedAt, deletedBy } : p)),
    );
  }, []);

  const handleEvent = useCallback(
    (event: RoomMessageEvent) => {
      if (event.type === 'CREATED') addMessage(event.message);
      else if (event.type === 'EDITED') upsertMessage(event.message);
      else if (event.type === 'DELETED')
        markDeleted(event.message.id, event.message.deletedAt, event.message.deletedBy);
    },
    [addMessage, upsertMessage, markDeleted],
  );

  const clearMessages = useCallback(() => {
    setMessages([]);
    setHasMore(true);
  }, []);

  return { messages, isLoading, hasMore, error, loadInitialMessages, loadMoreMessages, addMessage, upsertMessage, markDeleted, handleEvent, clearMessages };
};
