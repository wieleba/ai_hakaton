import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useRoom } from '../hooks/useRoom';
import { useRoomMessages } from '../hooks/useRoomMessages';
import { useWebSocket } from '../hooks/useWebSocket';
import { MessageList } from '../components/MessageList';
import { MessageInput, MessageInputHandle } from '../components/MessageInput';
import { ReplyPill } from '../components/ReplyPill';
import { EmojiPickerButton } from '../components/EmojiPickerButton';
import { ComposerAttachButton } from '../components/ComposerAttachButton';
import { AttachmentPreviewChip } from '../components/AttachmentPreviewChip';
import { roomService } from '../services/roomService';
import { messageService } from '../services/messageService';
import { useUnread } from '../hooks/useUnread';
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
  const [stagedFile, setStagedFile] = useState<File | null>(null);
  const { markRoomRead } = useUnread();

  useEffect(() => {
    if (!roomId) return;
    fetchRoom(roomId);
    loadInitialMessages(roomId);
    roomService.joinRoom(roomId).catch(() => {});
    markRoomRead(roomId);

    if (isConnected) {
      subscribe(roomId, handleEvent);
    }

    return () => {
      if (roomId) unsubscribe(roomId);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomId, isConnected]);

  const handleSend = async (text: string) => {
    if (!roomId) return;
    if (stagedFile) {
      try {
        await messageService.sendMessageWithAttachment(roomId, text, stagedFile, replyTarget?.id);
        setStagedFile(null);
        setReplyTarget(null);
      } catch (err) {
        console.error('Failed to send message with attachment:', err);
      }
      return;
    }
    if (!isConnected) return;
    try {
      sendWebSocketMessage(roomId, text, replyTarget?.id);
      setReplyTarget(null);
    } catch (err) {
      console.error('Failed to send message:', err);
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

  const handleReact = async (messageId: string, emoji: string) => {
    if (!roomId) return;
    await messageService.toggleReaction(roomId, messageId, emoji);
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
    <div className="h-full bg-gray-100 flex flex-col min-h-0 dark:bg-discord-base">
      <div className="bg-white shadow p-4 border-b dark:bg-discord-sidebar dark:border-discord-border">
        <div className="flex justify-between items-center">
          <div>
            <h1 className="text-2xl font-bold dark:text-discord-text">{currentRoom?.name || 'Loading...'}</h1>
            {currentRoom?.description && (
              <p className="text-gray-600 text-sm dark:text-discord-muted">{currentRoom.description}</p>
            )}
          </div>
          <button
            onClick={handleLeaveRoom}
            className="px-4 py-2 border rounded hover:bg-gray-100 dark:border-discord-border dark:text-discord-text dark:hover:bg-discord-hover"
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
            onReact={handleReact}
          />
          <MessageInput
            ref={inputRef}
            onSend={handleSend}
            disabled={!isConnected && !stagedFile}
            canSubmitWithoutText={!!stagedFile}
            onPasteFile={(f) => setStagedFile(f)}
            actions={
              <>
                <ComposerAttachButton onFile={(f) => setStagedFile(f)} disabled={!!stagedFile} />
                <EmojiPickerButton onPick={(e) => inputRef.current?.insertText(e)} />
                {stagedFile && (
                  <AttachmentPreviewChip file={stagedFile} onRemove={() => setStagedFile(null)} />
                )}
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
