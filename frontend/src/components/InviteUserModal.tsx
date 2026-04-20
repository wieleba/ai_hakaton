import React, { useState } from 'react';
import { useSearch } from '../hooks/useSearch';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onInvite: (username: string) => Promise<void>;
}

export const InviteUserModal: React.FC<Props> = ({ isOpen, onClose, onInvite }) => {
  const [username, setUsername] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [focused, setFocused] = useState(false);
  const { results, isLoading } = useSearch(username);

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

  const pick = (pickedUsername: string) => {
    setUsername(pickedUsername);
    setFocused(false);
  };

  if (!isOpen) return null;
  const showSuggestions =
    focused && username.trim().length > 0 && (isLoading || results.users.length > 0);

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 w-96 dark:bg-discord-sidebar dark:text-discord-text">
        <h2 className="text-xl font-bold mb-4">Invite user to this room</h2>
        <form onSubmit={submit}>
          <div className="mb-4">
            <input
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              onFocus={() => setFocused(true)}
              onBlur={() => setFocused(false)}
              placeholder="Username"
              disabled={busy}
              autoComplete="off"
              className="w-full border rounded px-3 py-2 dark:bg-discord-input dark:border-discord-border dark:text-discord-text dark:placeholder-discord-dim"
            />
            {showSuggestions && (
              <div className="mt-1 border rounded max-h-40 overflow-y-auto bg-gray-50 dark:bg-discord-base dark:border-discord-border">
                {isLoading && (
                  <div className="px-3 py-2 text-xs text-gray-400 dark:text-discord-dim">Searching…</div>
                )}
                {!isLoading && results.users.length === 0 && (
                  <div className="px-3 py-2 text-xs text-gray-400 dark:text-discord-dim">No matches</div>
                )}
                {results.users.map((u) => (
                  <button
                    key={u.id}
                    type="button"
                    onMouseDown={(e) => e.preventDefault()}
                    onClick={() => pick(u.username)}
                    className="block w-full text-left px-3 py-2 text-sm hover:bg-gray-100 dark:hover:bg-discord-hover dark:text-discord-text"
                  >
                    {u.username}
                  </button>
                ))}
              </div>
            )}
          </div>
          {error && <div className="text-red-500 text-sm mb-4">{error}</div>}
          <div className="flex gap-2 justify-end">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-gray-600 hover:bg-gray-100 rounded dark:text-discord-muted dark:hover:bg-discord-hover"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={busy || !username.trim()}
              className="px-4 py-2 bg-blue-500 text-white rounded disabled:bg-gray-400 dark:bg-discord-accent dark:hover:bg-indigo-500 dark:disabled:bg-discord-hover dark:disabled:text-discord-dim"
            >
              {busy ? 'Inviting...' : 'Send invitation'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
