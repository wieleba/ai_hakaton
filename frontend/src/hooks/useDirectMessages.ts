import { useCallback, useState } from 'react';
import type { DirectMessage } from '../types/directMessage';
import { directMessageService } from '../services/directMessageService';

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

  return { messages, hasMore, isLoading, loadInitial, loadMore, addMessage };
}
