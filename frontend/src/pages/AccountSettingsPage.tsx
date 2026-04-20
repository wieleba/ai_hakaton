import React, { useEffect, useState } from 'react';
import { authService } from '../services/authService';
import { ChangePasswordModal } from '../components/ChangePasswordModal';
import { DeleteAccountModal } from '../components/DeleteAccountModal';
import { jabberService, MyJabberCredentials } from '../services/jabberService';
import type { User } from '../types/auth';

export const AccountSettingsPage: React.FC = () => {
  const [user, setUser] = useState<User | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [jabber, setJabber] = useState<MyJabberCredentials | null>(null);
  const [copied, setCopied] = useState<'jid' | 'password' | null>(null);

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
    jabberService.getMyCredentials().then(setJabber).catch(() => setJabber(null));
  }, []);

  const copy = async (value: string, which: 'jid' | 'password') => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(which);
      setTimeout(() => setCopied(null), 1200);
    } catch {
      // ignore — clipboard may be unavailable in non-secure contexts
    }
  };

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

      <section className="mt-8 rounded-lg border bg-white p-6 dark:bg-discord-sidebar dark:border-discord-border">
        <h2 className="text-lg font-semibold mb-4 dark:text-discord-text">Jabber / XMPP bridge</h2>
        {jabber === null ? (
          <p className="text-sm text-gray-500 dark:text-discord-dim">Loading…</p>
        ) : jabber.available ? (
          <>
            <p className="text-sm text-gray-600 mb-4 dark:text-discord-muted">
              Use these credentials in any Jabber client (Pidgin, Gajim, Psi+, Conversations, Dino)
              to send and receive DMs alongside the Chat web UI. Messages sent from the client appear
              as Chat DMs here; DMs sent from Chat appear in the client.
            </p>
            <dl className="grid grid-cols-[max-content,1fr] gap-x-6 gap-y-3 text-sm">
              <dt className="text-gray-500 dark:text-discord-dim">JID</dt>
              <dd className="font-mono dark:text-discord-text flex items-center gap-2">
                <span>{jabber.jid}</span>
                <button
                  onClick={() => copy(jabber.jid, 'jid')}
                  className="text-xs px-2 py-0.5 border rounded hover:bg-gray-100 dark:border-discord-border dark:hover:bg-discord-hover"
                  type="button"
                >
                  {copied === 'jid' ? 'Copied' : 'Copy'}
                </button>
              </dd>
              <dt className="text-gray-500 dark:text-discord-dim">Server</dt>
              <dd className="font-mono dark:text-discord-text">
                {jabber.host}:{jabber.port}
              </dd>
              <dt className="text-gray-500 dark:text-discord-dim">Password</dt>
              <dd className="font-mono dark:text-discord-text flex items-center gap-2">
                <span>{jabber.password}</span>
                <button
                  onClick={() => jabber.password && copy(jabber.password, 'password')}
                  className="text-xs px-2 py-0.5 border rounded hover:bg-gray-100 dark:border-discord-border dark:hover:bg-discord-hover"
                  type="button"
                >
                  {copied === 'password' ? 'Copied' : 'Copy'}
                </button>
              </dd>
            </dl>
            <p className="mt-4 text-xs text-gray-500 dark:text-discord-dim">
              Plaintext auth, STARTTLS disabled (dev-only). Add{' '}
              <code>127.0.0.1 {jabber.host}</code> to your hosts file so the client can resolve the
              server name.
            </p>
          </>
        ) : (
          <p className="text-sm text-gray-600 dark:text-discord-muted">
            XMPP bridge unavailable for this username. Usernames with characters outside{' '}
            <code>a-z 0-9 . - _</code> aren&apos;t provisioned (the XMPP JID spec is strict), or
            provisioning failed at registration time.
          </p>
        )}
      </section>

      <ChangePasswordModal isOpen={passwordOpen} onClose={() => setPasswordOpen(false)} />
      <DeleteAccountModal isOpen={deleteOpen} onClose={() => setDeleteOpen(false)} />
    </div>
  );
};
