import React, { useState } from 'react';
import type { Message } from '../types/room';
import { MessageActionsMenu } from './MessageActionsMenu';
import { InlineMessageEditor } from './InlineMessageEditor';
import { ReactionsBar } from './ReactionsBar';
import { AttachmentRenderer } from './AttachmentRenderer';
import { YouTubeEmbed } from './YouTubeEmbed';
import { extractYouTubeIds } from '../utils/youtube';

interface Props {
  message: Message;
  currentUserId: string | null;
  onReply: (m: Message) => void;
  onEdit: (messageId: string, newText: string) => Promise<void>;
  onDelete: (messageId: string) => Promise<void>;
  onReact: (messageId: string, emoji: string) => void | Promise<void>;
}

export const MessageItem: React.FC<Props> = ({ message, currentUserId, onReply, onEdit, onDelete, onReact }) => {
  const [editing, setEditing] = useState(false);
  const isDeleted = !!message.deletedAt;
  const isAuthor = !!currentUserId && message.userId === currentUserId;
  const edited = !!message.editedAt;

  const timestamp = new Date(message.createdAt).toLocaleTimeString();

  if (isDeleted) {
    return (
      <div className="bg-gray-50 rounded p-3 border-l-4 border-gray-300 dark:bg-discord-sidebar dark:border-discord-border">
        <div className="flex justify-between items-baseline">
          <span className="font-semibold text-sm text-gray-400 dark:text-discord-dim">{message.username}</span>
          <span className="text-xs text-gray-400 dark:text-discord-dim">{timestamp}</span>
        </div>
        <p className="text-gray-400 italic mt-1 dark:text-discord-dim">Message deleted</p>
      </div>
    );
  }

  return (
    <div className="group relative bg-gray-50 rounded p-3 border-l-4 border-blue-500 dark:bg-discord-sidebar dark:border-discord-accent">
      <MessageActionsMenu
        isAuthor={isAuthor}
        onReply={() => onReply(message)}
        onEdit={() => setEditing(true)}
        onDelete={async () => {
          if (window.confirm('Delete this message?')) {
            await onDelete(message.id);
          }
        }}
        onReact={(e) => onReact(message.id, e)}
      />

      <div className="flex justify-between items-baseline">
        <span className="font-semibold text-sm dark:text-discord-text">{message.username}</span>
        <span className="text-xs text-gray-400 dark:text-discord-dim">
          {timestamp}
          {edited && <span className="ml-1">(edited)</span>}
        </span>
      </div>

      {message.replyTo && (
        <a
          href={`#msg-${message.replyTo.id}`}
          className="block border-l-2 border-gray-300 pl-2 mt-1 mb-1 text-xs text-gray-500 truncate"
        >
          <strong>@{message.replyTo.authorUsername}</strong>: {message.replyTo.textPreview}
        </a>
      )}

      <div id={`msg-${message.id}`}>
        {editing ? (
          <InlineMessageEditor
            initialText={message.text ?? ''}
            onSave={async (newText) => {
              await onEdit(message.id, newText);
              setEditing(false);
            }}
            onCancel={() => setEditing(false)}
          />
        ) : (
          <>
            {message.text && (
              <p className="text-gray-700 mt-1 whitespace-pre-wrap dark:text-discord-text">{message.text}</p>
            )}
            {extractYouTubeIds(message.text).map((id) => (
              <YouTubeEmbed key={id} videoId={id} />
            ))}
            {message.attachment && <AttachmentRenderer attachment={message.attachment} />}
          </>
        )}
      </div>
      <ReactionsBar
        reactions={message.reactions}
        onToggle={(e) => onReact(message.id, e)}
      />
    </div>
  );
};
