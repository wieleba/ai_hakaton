import { useCallback, useEffect, useState } from 'react';
import type { Friendship } from '../types/friendship';
import { friendshipService } from '../services/friendshipService';

export function useFriendRequests() {
  const [incoming, setIncoming] = useState<Friendship[]>([]);
  const [outgoing, setOutgoing] = useState<Friendship[]>([]);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      const [inc, out] = await Promise.all([
        friendshipService.listIncoming(),
        friendshipService.listOutgoing(),
      ]);
      setIncoming(inc);
      setOutgoing(out);
    } catch (e: any) {
      setError(e?.message ?? 'Failed to load requests');
    }
  }, []);

  useEffect(() => {
    reload();
  }, [reload]);

  const accept = useCallback(async (id: string) => {
    await friendshipService.accept(id);
    setIncoming((prev) => prev.filter((r) => r.id !== id));
  }, []);

  const reject = useCallback(async (id: string) => {
    await friendshipService.reject(id);
    setIncoming((prev) => prev.filter((r) => r.id !== id));
  }, []);

  const cancel = useCallback(async (id: string) => {
    await friendshipService.cancel(id);
    setOutgoing((prev) => prev.filter((r) => r.id !== id));
  }, []);

  const sendRequest = useCallback(
    async (username: string) => {
      const req = await friendshipService.sendRequest(username);
      if (req.status === 'accepted') {
        await reload(); // inverse-pending auto-accepted both sides
      } else {
        setOutgoing((prev) => [req, ...prev]);
      }
      return req;
    },
    [reload],
  );

  return { incoming, outgoing, error, sendRequest, accept, reject, cancel, reload };
}
