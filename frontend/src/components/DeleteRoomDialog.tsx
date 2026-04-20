import React, { useState } from 'react';

interface Props {
  isOpen: boolean;
  roomName: string;
  onConfirm: () => Promise<void>;
  onClose: () => void;
}

export const DeleteRoomDialog: React.FC<Props> = ({ isOpen, roomName, onConfirm, onClose }) => {
  const [busy, setBusy] = useState(false);

  if (!isOpen) return null;
  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 w-96 dark:bg-discord-sidebar dark:text-discord-text">
        <h2 className="text-xl font-bold mb-2">Delete room?</h2>
        <p className="text-sm text-gray-700 mb-4 dark:text-discord-muted">
          This will permanently delete <strong>{roomName}</strong> and all of its messages. This
          cannot be undone.
        </p>
        <div className="flex gap-2 justify-end">
          <button
            type="button"
            onClick={onClose}
            disabled={busy}
            className="px-4 py-2 text-gray-600 hover:bg-gray-100 rounded dark:text-discord-muted dark:hover:bg-discord-hover"
          >
            Cancel
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={async () => {
              setBusy(true);
              try {
                await onConfirm();
              } finally {
                setBusy(false);
              }
            }}
            className="px-4 py-2 bg-red-500 text-white rounded hover:bg-red-600 disabled:bg-gray-400"
          >
            {busy ? 'Deleting...' : 'Delete room'}
          </button>
        </div>
      </div>
    </div>
  );
};
