import { useEffect, useRef } from 'react';
import { websocketService } from '../services/websocketService';
import type { Subscription } from '../services/websocketService';
import type { DirectMessage, FriendEvent } from '../types/directMessage';

export type DirectMessageEvent =
  | { type: 'CREATED'; message: DirectMessage }
  | { type: 'EDITED'; message: DirectMessage }
  | { type: 'DELETED'; message: { id: string; deletedAt: string; deletedBy: string } };

type DmEventHandler = (event: DirectMessageEvent) => void;
type FriendEventHandler = (event: FriendEvent) => void;

export function useDirectMessageSocket(onDmEvent: DmEventHandler, onFriendEvent: FriendEventHandler) {
  const dmSubRef = useRef<Subscription | null>(null);
  const feSubRef = useRef<Subscription | null>(null);

  useEffect(() => {
    let mounted = true;
    const connect = async () => {
      const token = localStorage.getItem('authToken') || '';
      try {
        await websocketService.connect(token);
        if (!mounted) return;
        dmSubRef.current = websocketService.subscribe('/user/queue/dms', (msg) => {
          onDmEvent(msg as unknown as DirectMessageEvent);
        });
        feSubRef.current = websocketService.subscribe(
          '/user/queue/friend-events',
          (msg) => {
            onFriendEvent(msg as unknown as FriendEvent);
          },
        );
      } catch (e) {
        console.error('WebSocket connect failed', e);
      }
    };
    connect();
    return () => {
      mounted = false;
      try {
        dmSubRef.current?.unsubscribe();
      } catch {}
      try {
        feSubRef.current?.unsubscribe();
      } catch {}
    };
  }, [onDmEvent, onFriendEvent]);

  const sendDm = (conversationId: string, text: string, replyToId?: string) => {
    websocketService.send(`/app/dms/${conversationId}/message`, { text, replyToId: replyToId ?? null });
  };

  return { sendDm };
}
