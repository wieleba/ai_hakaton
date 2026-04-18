import { useCallback, useEffect, useMemo, useState } from 'react';
import type { RoomMemberView, RoomRole } from '../types/roomModeration';
import { roomService } from '../services/roomService';

export function useRoomMembersWithRole(
  roomId: string | undefined,
  currentUserId: string | undefined,
) {
  const [members, setMembers] = useState<RoomMemberView[]>([]);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    if (!roomId) return;
    try {
      setMembers(await roomService.listMembersWithRole(roomId));
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, [roomId]);

  useEffect(() => {
    reload();
  }, [reload]);

  const myRow = useMemo(
    () => members.find((m) => m.userId === currentUserId),
    [members, currentUserId],
  );
  const myRole: RoomRole | undefined = myRow?.role;
  const isAdmin = !!myRow && (myRow.isOwner || myRow.role === 'admin');
  const isOwner = !!myRow && myRow.isOwner;

  return { members, error, myRole, isAdmin, isOwner, reload };
}
