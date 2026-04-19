import React, { useEffect, useRef, useState } from 'react';
import EmojiPicker, { EmojiClickData } from 'emoji-picker-react';

interface Props {
  isAuthor: boolean;
  onReply: () => void;
  onEdit: () => void;
  onDelete: () => void;
  onReact: (emoji: string) => void;
}

export const MessageActionsMenu: React.FC<Props> = ({
  isAuthor,
  onReply,
  onEdit,
  onDelete,
  onReact,
}) => {
  const [pickerOpen, setPickerOpen] = useState(false);
  const wrapperRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!pickerOpen) return;
    const onDocClick = (e: MouseEvent) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setPickerOpen(false);
      }
    };
    document.addEventListener('mousedown', onDocClick);
    return () => document.removeEventListener('mousedown', onDocClick);
  }, [pickerOpen]);

  const handlePick = (d: EmojiClickData) => {
    onReact(d.emoji);
    setPickerOpen(false);
  };

  return (
    <div
      ref={wrapperRef}
      className="absolute top-1 right-2 hidden group-hover:flex gap-1 bg-white border rounded shadow px-1 py-0.5 text-xs"
    >
      <button
        type="button"
        onClick={() => setPickerOpen((v) => !v)}
        className="px-2 py-0.5 hover:bg-gray-100 rounded"
        aria-label="React"
      >
        😀+
      </button>
      <button onClick={onReply} className="px-2 py-0.5 hover:bg-gray-100 rounded" aria-label="Reply">
        ↩ Reply
      </button>
      {isAuthor && (
        <>
          <button onClick={onEdit} className="px-2 py-0.5 hover:bg-gray-100 rounded" aria-label="Edit">
            ✎ Edit
          </button>
          <button
            onClick={onDelete}
            className="px-2 py-0.5 hover:bg-red-50 text-red-600 rounded"
            aria-label="Delete"
          >
            🗑 Delete
          </button>
        </>
      )}
      {pickerOpen && (
        <div className="absolute top-full right-0 z-50 mt-1">
          <EmojiPicker onEmojiClick={handlePick} />
        </div>
      )}
    </div>
  );
};
