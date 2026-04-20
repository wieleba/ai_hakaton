import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { MessageList } from '../components/MessageList';
import { MessageInput, MessageInputHandle } from '../components/MessageInput';
import { ReplyPill } from '../components/ReplyPill';
import { EmojiPickerButton } from '../components/EmojiPickerButton';
import { ComposerAttachButton } from '../components/ComposerAttachButton';
import { AttachmentPreviewChip } from '../components/AttachmentPreviewChip';
import { useDirectMessages } from '../hooks/useDirectMessages';
import { useDirectMessageSocket } from '../hooks/useDirectMessageSocket';
import type { DirectMessageEvent } from '../hooks/useDirectMessageSocket';
import { directMessageService } from '../services/directMessageService';
import { useUnread } from '../hooks/useUnread';
import type { DirectMessage } from '../types/directMessage';
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

export const DirectChatPage: React.FC = () => {
  const { conversationId } = useParams<{ conversationId: string }>();
  const currentUserId = useMemo(() => getCurrentUserId(), []);
  const inputRef = useRef<MessageInputHandle>(null);
  const { messages, hasMore, isLoading, loadInitial, loadMore, handleEvent } =
    useDirectMessages(conversationId);

  const [replyTarget, setReplyTarget] = useState<DirectMessage | null>(null);
  const [stagedFile, setStagedFile] = useState<File | null>(null);

  const onDmEvent = useCallback(
    (event: DirectMessageEvent) => {
      if (event.type === 'CREATED' || event.type === 'EDITED') {
        if (event.message.conversationId !== conversationId) return;
      }
      handleEvent(event);
    },
    [conversationId, handleEvent],
  );

  const { sendDm } = useDirectMessageSocket(onDmEvent, () => {});
  const { markDmRead } = useUnread();

  useEffect(() => {
    if (!conversationId) return;
    loadInitial(conversationId);
    markDmRead(conversationId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversationId, loadInitial]);

  const handleSend = async (text: string) => {
    if (!conversationId) return;
    if (stagedFile) {
      try {
        await directMessageService.sendMessageWithAttachment(conversationId, text, stagedFile, replyTarget?.id);
        setStagedFile(null);
        setReplyTarget(null);
      } catch (err) {
        console.error('Failed to send DM with attachment:', err);
      }
      return;
    }
    try {
      sendDm(conversationId, text, replyTarget?.id);
      setReplyTarget(null);
    } catch (err) {
      console.error('Failed to send DM:', err);
    }
  };

  const handleEdit = async (messageId: string, newText: string) => {
    if (!conversationId) return;
    await directMessageService.editMessage(conversationId, messageId, newText);
  };

  const handleDelete = async (messageId: string) => {
    if (!conversationId) return;
    await directMessageService.deleteMessage(conversationId, messageId);
  };

  const handleReact = async (messageId: string, emoji: string) => {
    if (!conversationId) return;
    await directMessageService.toggleReaction(conversationId, messageId, emoji);
  };

  // Adapt DirectMessage → shape MessageList expects (with username).
  // DmEvent from WebSocket carries senderUsername; REST-fetched messages don't,
  // so fall back to a short userId prefix.
  const adapted: Message[] = messages.map((m) => ({
    id: m.id,
    roomId: m.conversationId,
    userId: m.senderId,
    username: m.senderUsername ?? (m.senderId ? String(m.senderId).slice(0, 8) : 'unknown'),
    text: m.text,
    createdAt: m.createdAt,
    editedAt: m.editedAt,
    deletedAt: m.deletedAt,
    deletedBy: m.deletedBy,
    replyTo: m.replyTo,
    reactions: m.reactions,
    attachment: m.attachment,
  }));

  const replyPreview = replyTarget && {
    authorUsername: replyTarget.senderUsername ?? 'unknown',
    textPreview: (replyTarget.text ?? '').slice(0, 100),
  };

  const onReplyAdapter = (msg: Message) => {
    const original = messages.find((m) => m.id === msg.id);
    if (original) setReplyTarget(original);
  };

  return (
    <div className="flex flex-col h-full min-h-0">
      <div className="bg-white shadow p-4 border-b dark:bg-discord-sidebar dark:border-discord-border">
        <h1 className="text-xl font-bold dark:text-discord-text">Direct Message</h1>
      </div>
      <MessageList
        messages={adapted}
        isLoading={isLoading}
        hasMore={hasMore}
        onLoadMore={loadMore}
        currentUserId={currentUserId}
        onReply={onReplyAdapter}
        onEdit={handleEdit}
        onDelete={handleDelete}
        onReact={handleReact}
      />
      <MessageInput
        ref={inputRef}
        onSend={handleSend}
        disabled={!conversationId}
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
  );
};
