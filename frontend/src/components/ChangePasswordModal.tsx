import React, { useState } from 'react';
import { accountService } from '../services/accountService';

interface Props {
  isOpen: boolean;
  onClose: () => void;
}

const MIN_LENGTH = 8;

export const ChangePasswordModal: React.FC<Props> = ({ isOpen, onClose }) => {
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reset = () => {
    setOldPassword('');
    setNewPassword('');
    setConfirmPassword('');
    setError(null);
    setBusy(false);
  };

  const close = () => {
    reset();
    onClose();
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (newPassword.length < MIN_LENGTH) {
      setError(`New password must be at least ${MIN_LENGTH} characters`);
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('New password and confirmation do not match');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await accountService.changePassword(oldPassword, newPassword);
      close();
    } catch (err) {
      const anyErr = err as { response?: { status?: number; data?: { message?: string } }; message?: string };
      if (anyErr.response?.status === 403) {
        setError('Old password is incorrect');
      } else if (anyErr.response?.status === 400) {
        setError('New password must be at least 8 characters');
      } else {
        setError(anyErr.response?.data?.message ?? anyErr.message ?? 'Password change failed');
      }
      setBusy(false);
    }
  };

  if (!isOpen) return null;
  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 w-96 dark:bg-discord-sidebar dark:text-discord-text">
        <h2 className="text-xl font-bold mb-4">Change password</h2>
        <form onSubmit={submit} className="space-y-3">
          <input
            type="password"
            value={oldPassword}
            onChange={(e) => setOldPassword(e.target.value)}
            placeholder="Current password"
            autoComplete="current-password"
            disabled={busy}
            className="w-full border rounded px-3 py-2 dark:bg-discord-input dark:border-discord-border dark:text-discord-text dark:placeholder-discord-dim"
          />
          <input
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            placeholder="New password (min 8 characters)"
            autoComplete="new-password"
            disabled={busy}
            className="w-full border rounded px-3 py-2 dark:bg-discord-input dark:border-discord-border dark:text-discord-text dark:placeholder-discord-dim"
          />
          <input
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            placeholder="Confirm new password"
            autoComplete="new-password"
            disabled={busy}
            className="w-full border rounded px-3 py-2 dark:bg-discord-input dark:border-discord-border dark:text-discord-text dark:placeholder-discord-dim"
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
              disabled={busy || !oldPassword || !newPassword || !confirmPassword}
              className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 disabled:bg-gray-400 dark:bg-discord-accent dark:hover:bg-indigo-500 dark:disabled:bg-discord-hover dark:disabled:text-discord-dim"
            >
              {busy ? 'Saving…' : 'Change password'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
