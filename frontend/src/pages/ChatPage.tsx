import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useRoom } from '../hooks/useRoom';
import { useRoomMessages } from '../hooks/useRoomMessages';
import { useWebSocket } from '../hooks/useWebSocket';
import { MessageList } from '../components/MessageList';
import { MessageInput, MessageInputHandle } from '../components/MessageInput';
import { ReplyPill } from '../components/ReplyPill';
import { EmojiPickerButton } from '../components/EmojiPickerButton';
import { roomService } from '../services/roomService';
import { messageService } from '../services/messageService';
import type { Message } from '../types/room';

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
  const inputRef = useRef<MessageInputHandle>(null);
  const { currentRoom, fetchRoom, leaveRoom } = useRoom();
  const { messages, loadInitialMessages, loadMoreMessages, handleEvent } =
    useRoomMessages(roomId);
  const {
    isConnected,
    subscribe,
    unsubscribe,
    sendMessage: sendWebSocketMessage,
  } = useWebSocket();

  const [replyTarget, setReplyTarget] = useState<Message | null>(null);

  useEffect(() => {
    if (!roomId) return;
    fetchRoom(roomId);
    loadInitialMessages(roomId);
    roomService.joinRoom(roomId).catch(() => {});

    if (isConnected) {
      subscribe(roomId, handleEvent);
    }

    return () => {
      if (roomId) unsubscribe(roomId);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomId, isConnected]);

  const handleSend = (text: string) => {
    if (roomId && isConnected) {
      try {
        sendWebSocketMessage(roomId, text, replyTarget?.id);
        setReplyTarget(null);
      } catch (err) {
        console.error('Failed to send message:', err);
      }
    }
  };

  const handleEdit = async (messageId: string, newText: string) => {
    if (!roomId) return;
    await messageService.editMessage(roomId, messageId, newText);
  };

  const handleDelete = async (messageId: string) => {
    if (!roomId) return;
    await messageService.deleteMessage(roomId, messageId);
  };

  const handleLeaveRoom = async () => {
    if (roomId) {
      await leaveRoom(roomId);
      navigate('/rooms');
    }
  };

  const replyPreview = replyTarget && {
    authorUsername: replyTarget.username,
    textPreview: (replyTarget.text ?? '').slice(0, 100),
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
            currentUserId={currentUserId}
            onReply={(m) => setReplyTarget(m)}
            onEdit={handleEdit}
            onDelete={handleDelete}
          />
          <MessageInput
            ref={inputRef}
            onSend={handleSend}
            disabled={!isConnected}
            actions={
              <>
                <EmojiPickerButton onPick={(e) => inputRef.current?.insertText(e)} />
                {replyPreview && (
                  <ReplyPill
                    authorUsername={replyPreview.authorUsername}
                    textPreview={replyPreview.textPreview}
                    onDismiss={() => setReplyTarget(null)}
                  />
                )}
              </>
            }
          />
        </div>
      </div>
    </div>
  );
};
