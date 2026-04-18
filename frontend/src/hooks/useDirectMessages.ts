import { useCallback, useState } from 'react';
import type { DirectMessage } from '../types/directMessage';
import { directMessageService } from '../services/directMessageService';
import type { DirectMessageEvent } from './useDirectMessageSocket';

export function useDirectMessages(conversationId: string | undefined) {
  const [messages, setMessages] = useState<DirectMessage[]>([]);
  const [hasMore, setHasMore] = useState(true);
  const [isLoading, setIsLoading] = useState(false);

  const loadInitial = useCallback(async (id: string) => {
    setIsLoading(true);
    try {
      const h = await directMessageService.getHistory(id, undefined, 50);
      setMessages(h);
      setHasMore(h.length === 50);
    } finally {
      setIsLoading(false);
    }
  }, []);

  const loadMore = useCallback(async () => {
    if (!conversationId || !hasMore) return;
    setIsLoading(true);
    try {
      const oldest = messages[messages.length - 1];
      const h = await directMessageService.getHistory(conversationId, oldest?.id, 50);
      setMessages((prev) => [...prev, ...h]);
      setHasMore(h.length === 50);
    } finally {
      setIsLoading(false);
    }
  }, [conversationId, messages, hasMore]);

  const addMessage = useCallback((m: DirectMessage) => {
    setMessages((prev) => [m, ...prev]);
  }, []);

  const upsertMessage = useCallback((m: DirectMessage) => {
    setMessages((prev) => prev.map((p) => (p.id === m.id ? { ...p, ...m } : p)));
  }, []);

  const markDeleted = useCallback((id: string, deletedAt: string, deletedBy: string) => {
    setMessages((prev) =>
      prev.map((p) => (p.id === id ? { ...p, text: null, deletedAt, deletedBy } : p)),
    );
  }, []);

  const handleEvent = useCallback(
    (event: DirectMessageEvent) => {
      if (event.type === 'CREATED') addMessage(event.message);
      else if (event.type === 'EDITED') upsertMessage(event.message);
      else if (event.type === 'DELETED')
        markDeleted(event.message.id, event.message.deletedAt, event.message.deletedBy);
    },
    [addMessage, upsertMessage, markDeleted],
  );

  return { messages, hasMore, isLoading, loadInitial, loadMore, addMessage, upsertMessage, markDeleted, handleEvent };
}
