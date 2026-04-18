import React, { useEffect, useMemo, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useRoom } from '../hooks/useRoom';
import { useRoomMessages } from '../hooks/useRoomMessages';
import { useWebSocket } from '../hooks/useWebSocket';
import { MessageList } from '../components/MessageList';
import { MessageInput } from '../components/MessageInput';
import { BanListPanel } from '../components/BanListPanel';
import { DeleteRoomDialog } from '../components/DeleteRoomDialog';
import { roomService } from '../services/roomService';
import { useRoomMembersWithRole } from '../hooks/useRoomMembersWithRole';
import { useRoomAdminActions } from '../hooks/useRoomAdminActions';

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
  const { isConnected, subscribe, unsubscribe, sendMessage: sendWebSocketMessage } = useWebSocket();

  const { isOwner } = useRoomMembersWithRole(roomId, currentUserId ?? undefined);
  const { deleteRoom } = useRoomAdminActions(roomId);
  const [bansOpen, setBansOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);

  useEffect(() => {
    if (!roomId) return;
    fetchRoom(roomId);
    loadInitialMessages(roomId);
    // Ensure membership on every entry. joinRoom is idempotent server-side,
    // so calling it after re-entry (when the user previously pressed Leave)
    // re-adds them so their sends are accepted. Swallow errors — the user
    // could hit this while the network hiccups; the history load still works.
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

  const handleDeleteRoom = async () => {
    await deleteRoom();
    navigate('/rooms');
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
          <div className="flex gap-2">
            {isOwner && (
              <button
                onClick={() => setDeleteOpen(true)}
                className="px-4 py-2 bg-red-500 text-white rounded hover:bg-red-600"
              >
                Delete Room
              </button>
            )}
            <button
              onClick={handleLeaveRoom}
              className="px-4 py-2 border rounded hover:bg-gray-100"
            >
              Leave Room
            </button>
          </div>
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

      {roomId && (
        <BanListPanel isOpen={bansOpen} onClose={() => setBansOpen(false)} roomId={roomId} />
      )}
      {roomId && currentRoom && (
        <DeleteRoomDialog
          isOpen={deleteOpen}
          roomName={currentRoom.name}
          onConfirm={handleDeleteRoom}
          onClose={() => setDeleteOpen(false)}
        />
      )}
    </div>
  );
};
