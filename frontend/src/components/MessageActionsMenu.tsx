import React from 'react';

interface Props {
  isAuthor: boolean;
  onReply: () => void;
  onEdit: () => void;
  onDelete: () => void;
}

export const MessageActionsMenu: React.FC<Props> = ({ isAuthor, onReply, onEdit, onDelete }) => (
  <div className="absolute top-1 right-2 hidden group-hover:flex gap-1 bg-white border rounded shadow px-1 py-0.5 text-xs">
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
  </div>
);
