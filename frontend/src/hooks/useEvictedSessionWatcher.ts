import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { websocketService } from '../services/websocketService';
import { authService } from '../services/authService';

function currentUserId(): string | null {
  const token = localStorage.getItem('authToken');
  if (!token) return null;
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return typeof payload.sub === 'string' ? payload.sub : null;
  } catch {
    return null;
  }
}

/**
 * Subscribes once per logged-in session to /user/{uuid}/queue/sessions. When the
 * server evicts *this* session (sessionId matches the live STOMP session id), clear
 * the auth token and redirect to /login. Evictions of sibling sessions are no-ops
 * here — the Sessions page polls and will pick them up on its next tick.
 */
export function useEvictedSessionWatcher(): void {
  const navigate = useNavigate();
  useEffect(() => {
    let sub: { unsubscribe: () => void } | null = null;

    const install = () => {
      if (!websocketService.isConnected()) return;
      const userId = currentUserId();
      if (!userId) return;
      try {
        sub = websocketService.subscribe(
          `/user/${userId}/queue/sessions`,
          (msg) => {
            const payload = msg as { type?: string; sessionId?: string };
            if (payload.type !== 'EVICTED') return;
            if (payload.sessionId && payload.sessionId === websocketService.getSessionId()) {
              authService.removeAuthToken();
              websocketService.disconnect();
              navigate('/login', { replace: true });
            }
          },
        );
      } catch {
        // WS not ready; retry on next mount / connection.
      }
    };

    install();
    const intervalId = window.setInterval(() => {
      if (sub) {
        window.clearInterval(intervalId);
        return;
      }
      install();
    }, 2000);

    return () => {
      window.clearInterval(intervalId);
      if (sub) sub.unsubscribe();
    };
  }, [navigate]);
}
