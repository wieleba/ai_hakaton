import { useState, useEffect, useCallback, useRef } from 'react';
import { websocketService, Subscription } from '../services/websocketService';
import type { Message } from '../types/room';

export type RoomMessageEvent =
  | { type: 'CREATED'; message: Message }
  | { type: 'EDITED'; message: Message }
  | { type: 'DELETED'; message: { id: string; deletedAt: string; deletedBy: string } };

/**
 * Hook for working with the shared STOMP WebSocket connection.
 *
 * The client itself is a singleton on `websocketService` — multiple pages
 * share one connection for the whole logged-in session. Each hook instance
 * owns the set of topic subscriptions it created and tears only those down
 * when the consumer unmounts, so leaving and re-entering a room works
 * without tearing down every other live subscription (DMs, friend-events).
 */
export const useWebSocket = () => {
  const [isConnected, setIsConnected] = useState(websocketService.isConnected());
  const [error, setError] = useState<string | null>(null);
  const subsRef = useRef<Map<string, Subscription>>(new Map());

  useEffect(() => {
    let cancelled = false;

    const doConnect = async () => {
      try {
        const token = localStorage.getItem('authToken') || '';
        await websocketService.connect(token);
        if (!cancelled) setIsConnected(true);
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : String(err));
      }
    };

    if (websocketService.isConnected()) {
      setIsConnected(true);
    } else {
      doConnect();
    }

    return () => {
      cancelled = true;
      // Tear down only subscriptions owned by *this* hook instance. We
      // intentionally do NOT disconnect the underlying client — other hooks
      // (DMs, friend events) may be sharing it.
      subsRef.current.forEach((sub) => sub.unsubscribe());
      subsRef.current.clear();
    };
  }, []);

  const subscribe = useCallback(
    (roomId: string, onEvent: (event: RoomMessageEvent) => void) => {
      if (!websocketService.isConnected()) {
        console.warn('WebSocket not connected; skipping subscribe');
        return;
      }
      // Replace any prior subscription for the same room (covers the case
      // where React fires the effect twice in StrictMode or where the caller
      // invokes subscribe twice for whatever reason).
      const existing = subsRef.current.get(roomId);
      if (existing) existing.unsubscribe();

      const sub = websocketService.subscribe(`/topic/room/${roomId}`, (msg) => {
        const payload = msg as unknown as RoomMessageEvent;
        onEvent(payload);
      });
      subsRef.current.set(roomId, sub);
    },
    [],
  );

  const unsubscribe = useCallback((roomId: string) => {
    const sub = subsRef.current.get(roomId);
    if (sub) {
      sub.unsubscribe();
      subsRef.current.delete(roomId);
    }
  }, []);

  const sendMessage = useCallback((roomId: string, text: string, replyToId?: string) => {
    if (!websocketService.isConnected()) {
      throw new Error('WebSocket not connected');
    }
    websocketService.send(`/app/rooms/${roomId}/message`, { text, replyToId: replyToId ?? null });
  }, []);

  const sendRaw = useCallback((destination: string, body: object = {}) => {
    if (!websocketService.isConnected()) {
      throw new Error('WebSocket not connected');
    }
    websocketService.send(destination, body as Record<string, unknown>);
  }, []);

  const subscribeDestination = useCallback(
    (destination: string, onPayload: (payload: Record<string, unknown>) => void) => {
      if (!websocketService.isConnected()) {
        console.warn('WebSocket not connected; skipping subscribe');
        return;
      }
      const existing = subsRef.current.get(destination);
      if (existing) existing.unsubscribe();

      const sub = websocketService.subscribe(destination, (msg) => {
        onPayload(msg);
      });
      subsRef.current.set(destination, sub);
    },
    [],
  );

  return { isConnected, error, subscribe, unsubscribe, sendMessage, sendRaw, subscribeDestination };
};
