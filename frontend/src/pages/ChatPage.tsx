import React, { useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useRoom } from '../hooks/useRoom';
import { useRoomMessages } from '../hooks/useRoomMessages';
import { useWebSocket } from '../hooks/useWebSocket';
import { MessageList } from '../components/MessageList';
import { MessageInput } from '../components/MessageInput';
import { roomService } from '../services/roomService';

export const ChatPage: React.FC = () => {
  const { roomId } = useParams<{ roomId: string }>();
  const navigate = useNavigate();
  const { currentRoom, fetchRoom, leaveRoom } = useRoom();
  const { messages, loadInitialMessages, loadMoreMessages, addMessage } = useRoomMessages(roomId);
  const { isConnected, subscribe, unsubscribe, sendMessage: sendWebSocketMessage } = useWebSocket();

  useEffect(() => {
    if (!roomId) return;
    fetchRoom(roomId);
    loadInitialMessages(roomId);
    // joinRoom is idempotent server-side and swallows for private rooms
    // where the caller is already a member (owner or invitee).
    roomService.joinRoom(roomId).catch(() => {});

    if (isConnected) {
      subscribe(roomId, addMessage);
    }

    return () => {
      if (roomId) unsubscribe(roomId);
    };
  }, [roomId, isConnected]);

  const handleSendMessage = (text: string) => {
    if (roomId && isConnected) {
      try {
        sendWebSocketMessage(roomId, text);
      } catch (err) {
        console.error('Failed to send message:', err);
      }
    }
  };

  const handleLeaveRoom = async () => {
    if (roomId) {
      await leaveRoom(roomId);
      navigate('/rooms');
    }
  };

  return (
    <div className="h-full bg-gray-100 flex flex-col min-h-0">
      <div className="bg-white shadow p-4 border-b">
        <div className="flex justify-between items-center">
          <div>
            <h1 className="text-2xl font-bold">{currentRoom?.name || 'Loading...'}</h1>
            {currentRoom?.description && (
              <p className="text-gray-600 text-sm">{currentRoom.description}</p>
            )}
          </div>
          <button
            onClick={handleLeaveRoom}
            className="px-4 py-2 border rounded hover:bg-gray-100"
          >
            Leave Room
          </button>
        </div>
      </div>

      <div className="flex flex-1 min-h-0 w-full">
        <div className="flex-1 flex flex-col min-w-0 min-h-0">
          <MessageList
            messages={messages}
            isLoading={false}
            hasMore={true}
            onLoadMore={loadMoreMessages}
          />
          <MessageInput onSend={handleSendMessage} disabled={!isConnected} />
        </div>
      </div>
    </div>
  );
};
