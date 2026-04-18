import { useCallback, useEffect, useState } from 'react';
import type { ConversationView } from '../types/directMessage';
import { directMessageService } from '../services/directMessageService';

export function useDirectConversations() {
  const [conversations, setConversations] = useState<ConversationView[]>([]);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      setConversations(await directMessageService.listConversations());
    } catch (e: any) {
      setError(e?.message ?? 'Failed to load conversations');
    }
  }, []);

  useEffect(() => {
    reload();
  }, [reload]);

  const bumpOnNewMessage = useCallback(
    (conversationId: string, text: string, at: string) => {
      setConversations((prev) => {
        const idx = prev.findIndex((c) => c.id === conversationId);
        if (idx < 0) return prev;
        const updated = { ...prev[idx], lastMessage: text, lastMessageAt: at };
        const rest = prev.filter((_, i) => i !== idx);
        return [updated, ...rest];
      });
    },
    [],
  );

  return { conversations, error, reload, bumpOnNewMessage };
}
