import { useCallback, useEffect, useState } from 'react';
import type { FriendView } from '../types/friendship';
import { friendshipService } from '../services/friendshipService';
import { banService } from '../services/banService';

export function useFriends() {
  const [friends, setFriends] = useState<FriendView[]>([]);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      setFriends(await friendshipService.listFriends());
    } catch (e: any) {
      setError(e?.message ?? 'Failed to load friends');
    }
  }, []);

  useEffect(() => {
    reload();
  }, [reload]);

  const removeFriend = useCallback(async (userId: string) => {
    await friendshipService.removeFriend(userId);
    setFriends((prev) => prev.filter((f) => f.userId !== userId));
  }, []);

  const banUser = useCallback(async (userId: string) => {
    await banService.banUser(userId);
    setFriends((prev) => prev.filter((f) => f.userId !== userId));
  }, []);

  return { friends, error, reload, removeFriend, banUser };
}
