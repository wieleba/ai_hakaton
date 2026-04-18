import React, { useState } from 'react';

interface Props {
  onSubmit: (username: string) => Promise<void>;
}

export const SendFriendRequestForm: React.FC<Props> = ({ onSubmit }) => {
  const [username, setUsername] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim()) return;
    setBusy(true);
    setError(null);
    try {
      await onSubmit(username.trim());
      setUsername('');
    } catch (e: any) {
      setError(e?.response?.data?.message ?? e?.message ?? 'Failed to send request');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="flex flex-col gap-2 mb-4">
      <div className="flex gap-2">
        <input
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          placeholder="Username"
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
      {error && <div className="text-red-500 text-sm">{error}</div>}
    </form>
  );
};
