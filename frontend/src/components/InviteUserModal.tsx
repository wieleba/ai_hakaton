import React, { useState } from 'react';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onInvite: (username: string) => Promise<void>;
}

export const InviteUserModal: React.FC<Props> = ({ isOpen, onClose, onInvite }) => {
  const [username, setUsername] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim()) return;
    setBusy(true);
    setError(null);
    try {
      await onInvite(username.trim());
      setUsername('');
      onClose();
    } catch (err) {
      const anyErr = err as { response?: { data?: { message?: string } }; message?: string };
      setError(anyErr?.response?.data?.message || anyErr?.message || 'Invite failed');
    } finally {
      setBusy(false);
    }
  };

  if (!isOpen) return null;
  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 w-96">
        <h2 className="text-xl font-bold mb-4">Invite user to this room</h2>
        <form onSubmit={submit}>
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="Username"
            disabled={busy}
            className="w-full border rounded px-3 py-2 mb-4"
          />
          {error && <div className="text-red-500 text-sm mb-4">{error}</div>}
          <div className="flex gap-2 justify-end">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-gray-600 hover:bg-gray-100 rounded"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={busy || !username.trim()}
              className="px-4 py-2 bg-blue-500 text-white rounded disabled:bg-gray-400"
            >
              {busy ? 'Inviting...' : 'Send invitation'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
