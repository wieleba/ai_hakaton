import { useCallback, useEffect, useState } from 'react';
import type { ChatRoom } from '../types/room';
import { roomService } from '../services/roomService';

export function useMyRooms() {
  const [rooms, setRooms] = useState<ChatRoom[]>([]);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      setRooms(await roomService.listMyRooms());
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, []);

  useEffect(() => {
    reload();
  }, [reload]);

  return { rooms, error, reload };
}
