import React, { useState } from 'react';
import { useSearch } from '../hooks/useSearch';

interface Props {
  onSubmit: (username: string) => Promise<void>;
}

export const SendFriendRequestForm: React.FC<Props> = ({ onSubmit }) => {
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
      await onSubmit(username.trim());
      setUsername('');
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } }; message?: string };
      setError(err?.response?.data?.message ?? err?.message ?? 'Failed to send request');
    } finally {
      setBusy(false);
    }
  };

  const pick = (picked: string) => {
    setUsername(picked);
    setFocused(false);
  };

  const showSuggestions =
    focused && username.trim().length > 0 && (isLoading || results.users.length > 0);

  return (
    <form onSubmit={submit} className="flex flex-col gap-2 mb-4">
      <div className="flex gap-2">
        <input
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          placeholder="Username"
          autoComplete="off"
          className="flex-1 border rounded px-3 py-2"
          disabled={busy}
        />
        <button
          type="submit"
          disabled={busy || !username.trim()}
          className="px-4 py-2 bg-blue-500 text-white rounded disabled:bg-gray-400"
        >
          {busy ? 'Sending...' : 'Send request'}
        </button>
      </div>
      {showSuggestions && (
        <div className="border rounded max-h-40 overflow-y-auto bg-gray-50">
          {isLoading && (
            <div className="px-3 py-2 text-xs text-gray-400">Searching…</div>
          )}
          {!isLoading && results.users.length === 0 && (
            <div className="px-3 py-2 text-xs text-gray-400">No matches</div>
          )}
          {results.users.map((u) => (
            <button
              key={u.id}
              type="button"
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => pick(u.username)}
              className="block w-full text-left px-3 py-2 text-sm hover:bg-gray-100"
            >
              {u.username}
            </button>
          ))}
        </div>
      )}
      {error && <div className="text-red-500 text-sm">{error}</div>}
    </form>
  );
};
