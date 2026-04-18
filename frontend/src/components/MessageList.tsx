import React, { useEffect, useRef } from 'react';
import { Message } from '../types/room';

interface MessageListProps {
  messages: Message[];
  isLoading: boolean;
  hasMore: boolean;
  onLoadMore: () => void;
}

export const MessageList: React.FC<MessageListProps> = ({ messages, isLoading, hasMore, onLoadMore }) => {
  const listRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (listRef.current) {
      listRef.current.scrollTop = 0;
    }
  }, [messages]);

  return (
    <div ref={listRef} className="flex-1 overflow-y-auto bg-white p-4 border rounded mb-4">
      {hasMore && !isLoading && (
        <button onClick={onLoadMore} className="w-full text-center text-blue-500 text-sm mb-4 hover:underline">Load older messages</button>
      )}
      {isLoading && <div className="text-center text-gray-500">Loading...</div>}
      {messages.length === 0 && !isLoading && <div className="text-center text-gray-500">No messages yet</div>}
      <div className="space-y-3">
        {messages.map((msg) => (
          <div key={msg.id} className="bg-gray-50 rounded p-3 border-l-4 border-blue-500">
            <div className="flex justify-between items-baseline">
              <span className="font-semibold text-sm">{msg.username}</span>
              <span className="text-xs text-gray-400">{new Date(msg.createdAt).toLocaleTimeString()}</span>
            </div>
            <p className="text-gray-700 mt-1">{msg.text}</p>
          </div>
        ))}
      </div>
    </div>
  );
};
