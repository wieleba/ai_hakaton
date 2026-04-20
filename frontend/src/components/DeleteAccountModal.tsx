import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { accountService } from '../services/accountService';

interface Props {
  isOpen: boolean;
  onClose: () => void;
}

const CONFIRM_PHRASE = 'DELETE';

export const DeleteAccountModal: React.FC<Props> = ({ isOpen, onClose }) => {
  const [typed, setTyped] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  const reset = () => {
    setTyped('');
    setError(null);
    setBusy(false);
  };

  const close = () => {
    reset();
    onClose();
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (typed !== CONFIRM_PHRASE) return;
    setBusy(true);
    setError(null);
    try {
      await accountService.deleteAccount();
      localStorage.removeItem('authToken');
      navigate('/login', { replace: true });
    } catch (err) {
      const anyErr = err as { message?: string };
      setError(anyErr.message ?? 'Deletion failed');
      setBusy(false);
    }
  };

  if (!isOpen) return null;
  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 w-[28rem] dark:bg-discord-sidebar dark:text-discord-text">
        <h2 className="text-xl font-bold mb-2 text-red-600 dark:text-red-400">Delete account</h2>
        <p className="text-sm text-gray-700 mb-3 dark:text-discord-muted">
          This permanently deletes your account and everything you own:
        </p>
        <ul className="text-sm text-gray-700 mb-4 list-disc pl-5 space-y-1 dark:text-discord-muted">
          <li>Every chat room you own (with all its messages)</li>
          <li>Your memberships in other rooms</li>
          <li>Your friendships, direct-message conversations, and bans</li>
          <li>Reactions and invitations you sent or received</li>
        </ul>
        <p className="text-sm text-gray-700 mb-3 dark:text-discord-muted">
          Messages you sent in other people's rooms stay, but your name becomes
          "Deleted user".
        </p>
        <form onSubmit={submit} className="space-y-3">
          <label className="block text-sm font-medium">
            Type <code className="bg-gray-100 px-1 rounded dark:bg-discord-input dark:text-discord-text">{CONFIRM_PHRASE}</code> to confirm:
          </label>
          <input
            type="text"
            value={typed}
            onChange={(e) => setTyped(e.target.value)}
            disabled={busy}
            autoComplete="off"
            className="w-full border rounded px-3 py-2 dark:bg-discord-input dark:border-discord-border dark:text-discord-text"
          />
          {error && <div className="text-red-500 text-sm">{error}</div>}
          <div className="flex gap-2 justify-end">
            <button
              type="button"
              onClick={close}
              disabled={busy}
              className="px-4 py-2 text-gray-600 hover:bg-gray-100 rounded dark:text-discord-muted dark:hover:bg-discord-hover"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={busy || typed !== CONFIRM_PHRASE}
              className="px-4 py-2 bg-red-500 text-white rounded hover:bg-red-600 disabled:bg-gray-400"
            >
              {busy ? 'Deleting…' : 'Delete account'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
