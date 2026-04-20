import React, { useEffect, useState } from 'react';
import { authService } from '../services/authService';
import { ChangePasswordModal } from '../components/ChangePasswordModal';
import { DeleteAccountModal } from '../components/DeleteAccountModal';
import type { User } from '../types/auth';

export const AccountSettingsPage: React.FC = () => {
  const [user, setUser] = useState<User | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);

  useEffect(() => {
    const token = localStorage.getItem('authToken');
    if (!token) {
      setError('Not signed in');
      return;
    }
    authService
      .getCurrentUser(token)
      .then(setUser)
      .catch((e) => setError(e instanceof Error ? e.message : String(e)));
  }, []);

  return (
    <div className="p-8 max-w-3xl">
      <h1 className="text-2xl font-bold mb-6 dark:text-discord-text">Account settings</h1>

      {error && <div className="mb-4 text-sm text-red-600">{error}</div>}

      <section className="mb-8 rounded-lg border bg-white p-6 dark:bg-discord-sidebar dark:border-discord-border">
        <h2 className="text-lg font-semibold mb-4 dark:text-discord-text">Your information</h2>
        {user === null && !error ? (
          <p className="text-sm text-gray-500 dark:text-discord-dim">Loading…</p>
        ) : user ? (
          <dl className="grid grid-cols-[max-content,1fr] gap-x-6 gap-y-3 text-sm">
            <dt className="text-gray-500 dark:text-discord-dim">Username</dt>
            <dd className="font-medium dark:text-discord-text">{user.username}</dd>
            <dt className="text-gray-500 dark:text-discord-dim">Email</dt>
            <dd className="font-medium dark:text-discord-text">{user.email}</dd>
            <dt className="text-gray-500 dark:text-discord-dim">User ID</dt>
            <dd className="font-mono text-xs text-gray-600 dark:text-discord-muted break-all">
              {user.id}
            </dd>
          </dl>
        ) : null}
      </section>

      <section className="rounded-lg border bg-white p-6 dark:bg-discord-sidebar dark:border-discord-border">
        <h2 className="text-lg font-semibold mb-4 dark:text-discord-text">Security</h2>

        <div className="flex flex-col gap-4">
          <div className="flex items-center justify-between">
            <div>
              <div className="font-medium dark:text-discord-text">Change password</div>
              <div className="text-sm text-gray-500 dark:text-discord-dim">
                Update the password you use to sign in.
              </div>
            </div>
            <button
              onClick={() => setPasswordOpen(true)}
              className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 dark:bg-discord-accent dark:hover:bg-indigo-500"
            >
              Change password
            </button>
          </div>

          <div className="border-t dark:border-discord-border" />

          <div className="flex items-center justify-between">
            <div>
              <div className="font-medium text-red-600 dark:text-red-400">Delete account</div>
              <div className="text-sm text-gray-500 dark:text-discord-dim">
                Permanently remove your account and the rooms you own.
              </div>
            </div>
            <button
              onClick={() => setDeleteOpen(true)}
              className="px-4 py-2 bg-red-500 text-white rounded hover:bg-red-600"
            >
              Delete account
            </button>
          </div>
        </div>
      </section>

      <ChangePasswordModal isOpen={passwordOpen} onClose={() => setPasswordOpen(false)} />
      <DeleteAccountModal isOpen={deleteOpen} onClose={() => setDeleteOpen(false)} />
    </div>
  );
};
