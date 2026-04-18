import { useEffect, useRef } from 'react';
import { websocketService } from '../services/websocketService';
import type { Subscription } from '../services/websocketService';
import type { DirectMessage, FriendEvent } from '../types/directMessage';

type DmHandler = (msg: DirectMessage) => void;
type FriendEventHandler = (event: FriendEvent) => void;

export function useDirectMessageSocket(onDm: DmHandler, onFriendEvent: FriendEventHandler) {
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
          onDm(msg as unknown as DirectMessage);
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
  }, [onDm, onFriendEvent]);

  const sendDm = (conversationId: string, text: string) => {
    websocketService.send(`/app/dms/${conversationId}/message`, { text });
  };

  return { sendDm };
}
