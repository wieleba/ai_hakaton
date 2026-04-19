import React, { useCallback, useEffect, useState } from 'react';
import { sessionsService } from '../services/sessionsService';
import type { SessionRow } from '../types/session';

const POLL_MS = 30_000;

function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

export const SessionsPage: React.FC = () => {
  const [rows, setRows] = useState<SessionRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const data = await sessionsService.list();
      setRows(data);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, []);

  useEffect(() => {
    load();
    const id = window.setInterval(load, POLL_MS);
    return () => window.clearInterval(id);
  }, [load]);

  const onLogout = async (sessionId: string) => {
    if (!window.confirm('Log out this session?')) return;
    setBusy(true);
    try {
      await sessionsService.logout(sessionId);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const onLogoutOthers = async () => {
    if (!window.confirm('Log out every other device?')) return;
    setBusy(true);
    try {
      await sessionsService.logoutOthers();
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const otherCount = rows ? rows.filter((r) => !r.current).length : 0;

  return (
    <div className="p-8 max-w-3xl">
      <div className="flex justify-between items-start mb-4">
        <div>
          <h1 className="text-2xl font-bold">Active sessions</h1>
          <p className="text-sm text-gray-500">WebSocket sessions currently tied to your account.</p>
        </div>
        <button
          onClick={onLogoutOthers}
          disabled={busy || otherCount === 0}
          className="px-3 py-2 border rounded text-sm disabled:opacity-50 hover:bg-red-50"
        >
          Log out everywhere else
        </button>
      </div>

      {error && <div className="mb-3 text-sm text-red-600">{error}</div>}

      {rows === null ? (
        <div className="text-sm text-gray-500">Loading…</div>
      ) : rows.length === 0 ? (
        <div className="text-sm text-gray-500">No active sessions found.</div>
      ) : (
        <table className="w-full border-t text-sm">
          <thead>
            <tr className="text-left text-xs uppercase text-gray-500">
              <th className="py-2">Device</th>
              <th className="py-2">IP</th>
              <th className="py-2">Connected</th>
              <th className="py-2">Last seen</th>
              <th className="py-2"></th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.sessionId} className="border-t align-top">
                <td className="py-2 pr-2 max-w-xs truncate" title={r.userAgent ?? ''}>
                  {r.userAgent ?? 'Unknown'}
                </td>
                <td className="py-2 pr-2">{r.remoteAddr ?? '—'}</td>
                <td className="py-2 pr-2">{formatDate(r.connectedAt)}</td>
                <td className="py-2 pr-2">{formatDate(r.lastSeen)}</td>
                <td className="py-2 text-right">
                  {r.current ? (
                    <span className="text-xs bg-green-100 text-green-800 px-2 py-0.5 rounded">This device</span>
                  ) : (
                    <button
                      onClick={() => onLogout(r.sessionId)}
                      disabled={busy}
                      className="text-xs px-2 py-1 border rounded hover:bg-red-50 disabled:opacity-50"
                    >
                      Log out
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
};
