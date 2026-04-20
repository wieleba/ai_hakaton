import React, { useCallback, useEffect, useState } from 'react';
import { jabberService, JabberServerStatus } from '../services/jabberService';

const POLL_MS = 10_000;

function dot(ok: boolean): JSX.Element {
  return (
    <span
      aria-label={ok ? 'up' : 'down'}
      className={`inline-block w-2.5 h-2.5 rounded-full ${
        ok ? 'bg-green-500' : 'bg-red-500'
      }`}
    />
  );
}

function num(v: number | null): string {
  return v === null ? '—' : String(v);
}

export const JabberPage: React.FC = () => {
  const [rows, setRows] = useState<JabberServerStatus[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      setRows(await jabberService.getStatus());
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, []);

  useEffect(() => {
    reload();
    const id = window.setInterval(reload, POLL_MS);
    return () => window.clearInterval(id);
  }, [reload]);

  const federationActive =
    rows !== null &&
    rows.some((r) => (r.outgoingS2sConnections ?? 0) > 0 || (r.incomingS2sConnections ?? 0) > 0);

  return (
    <div className="p-8 max-w-5xl">
      <h1 className="text-2xl font-bold mb-2 dark:text-discord-text">Jabber (XMPP) servers</h1>
      <p className="text-sm text-gray-600 mb-6 dark:text-discord-muted">
        Connection dashboard + federation traffic for the two XMPP servers running alongside the
        chat stack. Any XMPP client (Pidgin, Gajim, Psi, Conversations, Dino…) can connect directly.
      </p>

      {error && <div className="mb-4 text-sm text-red-600">{error}</div>}

      {rows === null && !error ? (
        <p className="text-sm text-gray-500 dark:text-discord-dim">Loading…</p>
      ) : (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-8">
            {rows?.map((s) => (
              <article
                key={s.label}
                className="rounded-lg border bg-white p-5 dark:bg-discord-sidebar dark:border-discord-border"
              >
                <header className="flex items-center gap-2 mb-3">
                  <h2 className="text-lg font-semibold dark:text-discord-text">
                    Server {s.label}
                  </h2>
                  <code className="text-xs text-gray-500 dark:text-discord-dim">{s.domain}</code>
                </header>
                <dl className="grid grid-cols-[max-content,1fr] gap-x-4 gap-y-2 text-sm">
                  <dt className="text-gray-500 dark:text-discord-dim">Client (c2s)</dt>
                  <dd className="dark:text-discord-text flex items-center gap-2">
                    {dot(s.clientReachable)}
                    {s.host}:{s.clientPort}
                  </dd>

                  <dt className="text-gray-500 dark:text-discord-dim">Server-to-server</dt>
                  <dd className="dark:text-discord-text flex items-center gap-2">
                    {dot(s.s2sReachable)}
                    port {s.s2sPort}
                  </dd>

                  <dt className="text-gray-500 dark:text-discord-dim">HTTP API</dt>
                  <dd className="dark:text-discord-text flex items-center gap-2">
                    {dot(s.httpApiReachable)}
                    {s.httpApiReachable ? 'admin REST reachable' : 'unreachable'}
                  </dd>

                  <dt className="text-gray-500 dark:text-discord-dim">Registered users</dt>
                  <dd className="dark:text-discord-text tabular-nums">{num(s.registeredUsers)}</dd>

                  <dt className="text-gray-500 dark:text-discord-dim">Online users</dt>
                  <dd className="dark:text-discord-text tabular-nums">{num(s.onlineUsers)}</dd>

                  <dt className="text-gray-500 dark:text-discord-dim">S2S in / out</dt>
                  <dd className="dark:text-discord-text tabular-nums">
                    {num(s.incomingS2sConnections)} / {num(s.outgoingS2sConnections)}
                  </dd>
                </dl>
              </article>
            ))}
          </div>

          <section className="rounded-lg border bg-white p-5 mb-8 dark:bg-discord-sidebar dark:border-discord-border">
            <div className="flex items-center gap-2 mb-2">
              <h2 className="text-lg font-semibold dark:text-discord-text">Federation</h2>
              {dot(federationActive)}
              <span className="text-sm text-gray-500 dark:text-discord-dim">
                {federationActive ? 'active' : 'idle (no open S2S connections)'}
              </span>
            </div>
            <p className="text-sm text-gray-600 dark:text-discord-muted">
              The two servers peer over XMPP S2S (port 5269). They advertise reachability via
              Docker DNS aliases — <code>chat-a.local</code> / <code>chat-b.local</code>. Sending a
              message from a user on one domain to a user on the other establishes an outgoing S2S
              connection on the sender and an incoming one on the receiver; both show up live in
              the counts above.
            </p>
          </section>

          <section className="rounded-lg border bg-white p-5 dark:bg-discord-sidebar dark:border-discord-border">
            <h2 className="text-lg font-semibold mb-3 dark:text-discord-text">
              Connect with a Jabber client
            </h2>
            <p className="text-sm text-gray-600 mb-4 dark:text-discord-muted">
              Add the entries below to <code>/etc/hosts</code> so your client can resolve the
              server domains (macOS / Linux need sudo; Windows:
              {' '}
              <code>C:\Windows\System32\drivers\etc\hosts</code>):
            </p>
            <pre className="bg-gray-100 rounded p-3 text-xs mb-4 overflow-x-auto dark:bg-discord-base dark:text-discord-muted">{`127.0.0.1  chat-a.local
127.0.0.1  chat-b.local`}</pre>
            <p className="text-sm text-gray-600 mb-2 dark:text-discord-muted">Demo credentials:</p>
            <ul className="text-sm space-y-1 dark:text-discord-text mb-4">
              <li>
                <strong>Server A</strong>: <code>alice@chat-a.local</code> /{' '}
                <code>alicepass</code> · port <code>5222</code>
              </li>
              <li>
                <strong>Server B</strong>: <code>bob@chat-b.local</code> / <code>bobpass</code> ·
                port <code>5223</code> (host-mapped)
              </li>
            </ul>
            <p className="text-sm text-gray-600 dark:text-discord-muted">
              STARTTLS is disabled in this dev setup — configure your client for{' '}
              <em>plaintext authentication over unencrypted connection</em>. Cross-server messaging
              (alice → bob) exercises the federation S2S path.
            </p>
          </section>
        </>
      )}
    </div>
  );
};
