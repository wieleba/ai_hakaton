import { useEffect, useRef } from 'react';
import { websocketService } from '../services/websocketService';

const IDLE_MS = 60_000;
const TICK_MS = 15_000;
const HEARTBEAT_MS = 30_000;
const ACTIVITY_THROTTLE_MS = 5_000;

/** Installed once per app mount. Watches user activity, flips AFK state, and heartbeats. */
export function useAfkTracking(): void {
  const lastActivityAtRef = useRef<number>(Date.now());
  const currentStateRef = useRef<'ACTIVE' | 'AFK'>('ACTIVE');
  const lastActivityReportAtRef = useRef<number>(0);

  useEffect(() => {
    const bumpActivity = () => {
      const now = Date.now();
      lastActivityAtRef.current = now;
      if (now - lastActivityReportAtRef.current < ACTIVITY_THROTTLE_MS) return;
      lastActivityReportAtRef.current = now;
      // If we're coming back from AFK, flip immediately (don't wait 15s tick).
      if (currentStateRef.current === 'AFK' && websocketService.isConnected()) {
        try {
          websocketService.send('/app/presence/active', {});
          currentStateRef.current = 'ACTIVE';
        } catch {
          /* not connected yet */
        }
      }
    };

    const events: Array<keyof WindowEventMap> = ['mousemove', 'keydown', 'touchstart', 'focus'];
    for (const e of events) window.addEventListener(e, bumpActivity, { passive: true });

    const tickHandle = setInterval(() => {
      const idleFor = Date.now() - lastActivityAtRef.current;
      if (!websocketService.isConnected()) return;
      if (idleFor >= IDLE_MS && currentStateRef.current === 'ACTIVE') {
        try {
          websocketService.send('/app/presence/afk', {});
          currentStateRef.current = 'AFK';
        } catch {
          /* ignore */
        }
      } else if (idleFor < IDLE_MS && currentStateRef.current === 'AFK') {
        try {
          websocketService.send('/app/presence/active', {});
          currentStateRef.current = 'ACTIVE';
        } catch {
          /* ignore */
        }
      }
    }, TICK_MS);

    const heartbeatHandle = setInterval(() => {
      if (!websocketService.isConnected()) return;
      try {
        websocketService.send('/app/presence/heartbeat', {});
      } catch {
        /* ignore */
      }
    }, HEARTBEAT_MS);

    return () => {
      for (const e of events) window.removeEventListener(e, bumpActivity);
      clearInterval(tickHandle);
      clearInterval(heartbeatHandle);
    };
  }, []);
}
