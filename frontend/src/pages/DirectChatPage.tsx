import React, { useCallback, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { MessageList } from '../components/MessageList';
import { MessageInput } from '../components/MessageInput';
import { useDirectMessages } from '../hooks/useDirectMessages';
import { useDirectMessageSocket } from '../hooks/useDirectMessageSocket';
import type { DirectMessage } from '../types/directMessage';
import type { Message } from '../types/room';

export const DirectChatPage: React.FC = () => {
  const { conversationId } = useParams<{ conversationId: string }>();
  const { messages, hasMore, isLoading, loadInitial, loadMore, addMessage } =
    useDirectMessages(conversationId);

  const onDm = useCallback(
    (dm: DirectMessage) => {
      if (dm.conversationId === conversationId) addMessage(dm);
    },
    [conversationId, addMessage],
  );

  const { sendDm } = useDirectMessageSocket(onDm, () => {});

  useEffect(() => {
    if (conversationId) loadInitial(conversationId);
  }, [conversationId, loadInitial]);

  const handleSend = (text: string) => {
    if (conversationId) sendDm(conversationId, text);
  };

  // Adapt DirectMessage → shape MessageList expects (with username).
  // DmEvent from WebSocket carries senderUsername; REST-fetched messages don't,
  // so fall back to a short userId prefix.
  const adapted: Message[] = messages.map((m) => ({
    id: m.id,
    roomId: m.conversationId,
    userId: m.senderId,
    username: (m as any).senderUsername ?? (m.senderId ? String(m.senderId).slice(0, 8) : 'unknown'),
    text: m.text,
    createdAt: m.createdAt,
  }));

  return (
    <div className="flex flex-col h-full min-h-0">
      <div className="bg-white shadow p-4 border-b">
        <h1 className="text-xl font-bold">Direct Message</h1>
      </div>
      <MessageList
        messages={adapted}
        isLoading={isLoading}
        hasMore={hasMore}
        onLoadMore={loadMore}
      />
      <MessageInput onSend={handleSend} disabled={!conversationId} />
    </div>
  );
};
