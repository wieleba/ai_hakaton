import React from 'react';
import { ConversationList } from '../components/ConversationList';
import { useDirectConversations } from '../hooks/useDirectConversations';

export const DirectMessagesPage: React.FC = () => {
  const { conversations, error } = useDirectConversations();
  return (
    <div className="max-w-2xl mx-auto p-6 space-y-4 overflow-y-auto h-full">
      <h1 className="text-2xl font-bold">Direct Messages</h1>
      {error && <div className="text-red-500">{error}</div>}
      <ConversationList conversations={conversations} />
    </div>
  );
};
