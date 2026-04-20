import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { websocketService } from '../services/websocketService';
import { authService } from '../services/authService';
import { sessionsService } from '../services/sessionsService';

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
    let evicted = false;

    const redirectEvicted = () => {
      if (evicted) return;
      evicted = true;
      authService.removeAuthToken();
      websocketService.disconnect();
      navigate('/login', { replace: true });
    };

    // Backstop poll: Spring's user-destination fan-out only reliably hits one
    // of the user's active sessions, so we also check periodically whether
    // our own STOMP session id has disappeared from /api/sessions — or whether
    // /api/sessions itself 401s, meaning our JWT was just revoked.
    const pollEvicted = async () => {
      const mySid = websocketService.getSessionId();
      if (!mySid) return;
      if (!localStorage.getItem('authToken')) return;
      try {
        const rows = await sessionsService.list();
        if (!rows.some((r) => r.sessionId === mySid)) {
          redirectEvicted();
        }
      } catch {
        // 401 from a revoked token also means evicted.
        redirectEvicted();
      }
    };

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
              redirectEvicted();
            }
          },
        );
      } catch {
        // WS not ready; retry on next mount / connection.
      }
    };

    install();
    const subRetryId = window.setInterval(() => {
      if (sub) {
        window.clearInterval(subRetryId);
        return;
      }
      install();
    }, 2000);
    const pollId = window.setInterval(pollEvicted, 3000);

    return () => {
      window.clearInterval(subRetryId);
      window.clearInterval(pollId);
      if (sub) sub.unsubscribe();
    };
  }, [navigate]);
}
