import React, { useEffect, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useRoom } from '../hooks/useRoom';
import { useRoomMessages } from '../hooks/useRoomMessages';
import { useWebSocket } from '../hooks/useWebSocket';
import { MessageList } from '../components/MessageList';
import { MessageInput } from '../components/MessageInput';
import { RoomMembersPanel } from '../components/RoomMembersPanel';

const getCurrentUserId = (): string | null => {
  const token = localStorage.getItem('authToken');
  if (!token) return null;
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return typeof payload.sub === 'string' ? payload.sub : null;
  } catch {
    return null;
  }
};

export const ChatPage: React.FC = () => {
  const { roomId } = useParams<{ roomId: string }>();
  const navigate = useNavigate();
  const currentUserId = useMemo(() => getCurrentUserId(), []);
  const { currentRoom, fetchRoom, leaveRoom } = useRoom();
  const { messages, loadInitialMessages, loadMoreMessages, addMessage } = useRoomMessages(roomId);
  const { isConnected, subscribe, sendMessage: sendWebSocketMessage } = useWebSocket();

  useEffect(() => {
    if (roomId) {
      fetchRoom(roomId);
      loadInitialMessages(roomId);
      if (isConnected) {
        subscribe(roomId, addMessage);
      }
    }
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
    // h-full (not min-h-screen) so the page exactly fills the AppSidebar's
    // main pane. Otherwise with many messages the page grows past the
    // viewport and the MessageInput is pushed off-screen.
    <div className="h-full bg-gray-100 flex flex-col min-h-0">
      <div className="bg-white shadow p-4 border-b">
        <div className="max-w-6xl mx-auto flex justify-between items-center">
          <div>
            <h1 className="text-2xl font-bold">{currentRoom?.name || 'Loading...'}</h1>
            {currentRoom?.description && (
              <p className="text-gray-600 text-sm">{currentRoom.description}</p>
            )}
          </div>
          <button
            onClick={handleLeaveRoom}
            className="px-4 py-2 bg-red-500 text-white rounded hover:bg-red-600"
          >
            Leave Room
          </button>
        </div>
      </div>

      <div className="flex flex-1 min-h-0 max-w-6xl mx-auto w-full">
        <div className="flex-1 flex flex-col min-w-0 min-h-0">
          <MessageList
            messages={messages}
            isLoading={false}
            hasMore={true}
            onLoadMore={loadMoreMessages}
          />
          <MessageInput onSend={handleSendMessage} disabled={!isConnected} />
        </div>
        {roomId && currentUserId && (
          <RoomMembersPanel roomId={roomId} currentUserId={currentUserId} />
        )}
      </div>
    </div>
  );
};
