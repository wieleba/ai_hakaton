import React, { useEffect, useRef } from 'react';
import type { Message } from '../types/room';
import { MessageItem } from './MessageItem';

interface MessageListProps {
  messages: Message[];
  isLoading: boolean;
  hasMore: boolean;
  onLoadMore: () => void;
  currentUserId: string | null;
  onReply: (m: Message) => void;
  onEdit: (messageId: string, newText: string) => Promise<void>;
  onDelete: (messageId: string) => Promise<void>;
}

export const MessageList: React.FC<MessageListProps> = ({
  messages,
  isLoading,
  hasMore,
  onLoadMore,
  currentUserId,
  onReply,
  onEdit,
  onDelete,
}) => {
  const listRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (listRef.current) {
      listRef.current.scrollTop = 0;
    }
  }, [messages]);

  return (
    <div
      ref={listRef}
      className="flex-1 min-h-0 overflow-y-auto bg-white p-4 border rounded mb-4"
    >
      {hasMore && !isLoading && (
        <button
          onClick={onLoadMore}
          className="w-full text-center text-blue-500 text-sm mb-4 hover:underline"
        >
          Load older messages
        </button>
      )}
      {isLoading && <div className="text-center text-gray-500">Loading...</div>}
      {messages.length === 0 && !isLoading && (
        <div className="text-center text-gray-500">No messages yet</div>
      )}
      <div className="space-y-3">
        {messages.map((m) => (
          <MessageItem
            key={m.id}
            message={m}
            currentUserId={currentUserId}
            onReply={onReply}
            onEdit={onEdit}
            onDelete={onDelete}
          />
        ))}
      </div>
    </div>
  );
};
