import { useSyncExternalStore } from 'react';
import { websocketService } from '../services/websocketService';
import { unreadService } from '../services/unreadService';
import type { UnreadBump } from '../types/unread';

/**
 * Module-scope singleton for unread counts.
 * Mutations bump a version counter; useSyncExternalStore drives re-renders.
 */
const rooms = new Map<string, number>();
const dms = new Map<string, number>();
const listeners = new Set<() => void>();
let version = 0;
let initialFetchStarted = false;
let globalSubscription: { unsubscribe: () => void } | null = null;
let wsPollId: number | null = null;

function notify() {
  version += 1;
  listeners.forEach((l) => l());
}

function getUserIdFromToken(): string | null {
  const token = localStorage.getItem('authToken');
  if (!token) return null;
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return typeof payload.sub === 'string' ? payload.sub : null;
  } catch {
    return null;
  }
}

async function fetchInitial() {
  if (initialFetchStarted) return;
  initialFetchStarted = true;
  try {
    const data = await unreadService.counts();
    rooms.clear();
    dms.clear();
    for (const [k, v] of Object.entries(data.rooms)) rooms.set(k, v);
    for (const [k, v] of Object.entries(data.dms)) dms.set(k, v);
    notify();
  } catch {
    // Unauthenticated or transient; leave empty, retry on next hook mount.
    initialFetchStarted = false;
  }
}

function ensureSubscribed() {
  if (globalSubscription) return;
  if (!websocketService.isConnected()) return;
  const userId = getUserIdFromToken();
  if (!userId) return;
  try {
    globalSubscription = websocketService.subscribe(
      `/user/${userId}/queue/unread`,
      (msg) => {
        const bump = msg as unknown as UnreadBump;
        if (!bump?.chatId || !bump?.chatType) return;
        const map = bump.chatType === 'ROOM' ? rooms : dms;
        map.set(bump.chatId, (map.get(bump.chatId) ?? 0) + 1);
        notify();
      },
    );
  } catch {
    // WS transiently unavailable; retry on next poll.
  }
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  fetchInitial();
  ensureSubscribed();
  if (wsPollId === null) {
    wsPollId = window.setInterval(() => {
      if (!globalSubscription) ensureSubscribed();
    }, 2000);
  }
  return () => {
    listeners.delete(listener);
    if (listeners.size === 0) {
      if (globalSubscription) {
        globalSubscription.unsubscribe();
        globalSubscription = null;
      }
      if (wsPollId !== null) {
        window.clearInterval(wsPollId);
        wsPollId = null;
      }
      initialFetchStarted = false;
    }
  };
}

function getSnapshot(): number {
  return version;
}

export function useUnread() {
  useSyncExternalStore(subscribe, getSnapshot, getSnapshot);

  return {
    roomCount: (roomId: string) => rooms.get(roomId) ?? 0,
    dmCount: (conversationId: string) => dms.get(conversationId) ?? 0,
    markRoomRead: async (roomId: string) => {
      if (rooms.has(roomId)) {
        rooms.delete(roomId);
        notify();
      }
      try {
        await unreadService.markRoomRead(roomId);
      } catch {
        /* non-fatal */
      }
    },
    markDmRead: async (conversationId: string) => {
      if (dms.has(conversationId)) {
        dms.delete(conversationId);
        notify();
      }
      try {
        await unreadService.markDmRead(conversationId);
      } catch {
        /* non-fatal */
      }
    },
  };
}

/**
 * Reset hook used when auth changes (logout/login) so the singleton state doesn't
 * leak across users. Safe to call multiple times.
 */
export function resetUnread(): void {
  rooms.clear();
  dms.clear();
  if (globalSubscription) {
    globalSubscription.unsubscribe();
    globalSubscription = null;
  }
  initialFetchStarted = false;
  notify();
}

export function __testResetUnread() {
  resetUnread();
  listeners.clear();
  if (wsPollId !== null) {
    window.clearInterval(wsPollId);
    wsPollId = null;
  }
  version = 0;
}

