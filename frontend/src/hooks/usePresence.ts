import { useEffect, useState, useCallback } from 'react';
import { presenceService } from '../services/presenceService';
import type { PresenceState } from '../types/presence';
import { websocketService } from '../services/websocketService';

// Shared, module-scope state: one subscription + one map for the whole app.
const presenceMap: Map<string, PresenceState> = new Map();
const listeners: Set<() => void> = new Set();
let globalSubscription: { unsubscribe: () => void } | null = null;

function notifyAll() {
  for (const l of listeners) l();
}

function ensureSubscribed() {
  if (globalSubscription) return;
  if (!websocketService.isConnected()) return;
  globalSubscription = websocketService.subscribe('/topic/presence', (payload) => {
    const p = payload as { userId?: string; state?: PresenceState };
    if (p && p.userId && p.state) {
      presenceMap.set(p.userId, p.state);
      notifyAll();
    }
  });
}

/** Subscribe to presence and load initial snapshot for the given user ids. */
export function usePresence(userIds: string[]): (id: string) => PresenceState {
  const [, forceRender] = useState(0);

  // Register a rerender-on-change listener
  useEffect(() => {
    const listener = () => forceRender((x) => x + 1);
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  }, []);

  // Install global /topic/presence subscription once (lazily, as soon as WS is connected).
  useEffect(() => {
    let retryHandle: ReturnType<typeof setTimeout> | null = null;
    const tryInstall = () => {
      if (websocketService.isConnected()) {
        ensureSubscribed();
      } else {
        retryHandle = setTimeout(tryInstall, 500);
      }
    };
    tryInstall();
    return () => {
      if (retryHandle) clearTimeout(retryHandle);
    };
  }, []);

  // Load snapshot for the ids the caller cares about. Runs on mount + when
  // the id set changes meaningfully.
  const idsKey = userIds.slice().sort().join(',');
  useEffect(() => {
    if (userIds.length === 0) return;
    let cancelled = false;
    (async () => {
      try {
        const snap = await presenceService.snapshot(userIds);
        if (cancelled) return;
        for (const [id, state] of Object.entries(snap)) {
          presenceMap.set(id, state);
        }
        notifyAll();
      } catch (e) {
        console.warn('Failed to load presence snapshot', e);
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [idsKey]);

  return useCallback((id: string) => presenceMap.get(id) ?? 'OFFLINE', []);
}
