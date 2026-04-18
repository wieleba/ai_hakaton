import { useState, useCallback } from 'react';
import { Message } from '../types/room';
import { messageService } from '../services/messageService';

export const useRoomMessages = (roomId?: string) => {
  const [messages, setMessages] = useState<Message[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadInitialMessages = useCallback(async (id: string) => {
    setIsLoading(true);
    setError(null);
    try {
      const history = await messageService.getMessageHistory(id, undefined, 50);
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
        const history = await messageService.getMessageHistory(roomId, oldestMessage?.id, 50);
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

  const clearMessages = useCallback(() => {
    setMessages([]);
    setHasMore(true);
  }, []);

  return { messages, isLoading, hasMore, error, loadInitialMessages, loadMoreMessages, addMessage, clearMessages };
};
