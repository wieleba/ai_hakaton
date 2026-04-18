import { useCallback } from 'react';
import { roomService } from '../services/roomService';
import { roomInvitationService } from '../services/roomInvitationService';

export function useRoomAdminActions(roomId: string | undefined) {
  const kick = useCallback(
    async (userId: string) => {
      if (!roomId) return;
      await roomService.kickMember(roomId, userId);
    },
    [roomId],
  );

  const promote = useCallback(
    async (userId: string) => {
      if (!roomId) return;
      await roomService.promoteAdmin(roomId, userId);
    },
    [roomId],
  );

  const demote = useCallback(
    async (userId: string) => {
      if (!roomId) return;
      await roomService.demoteAdmin(roomId, userId);
    },
    [roomId],
  );

  const unban = useCallback(
    async (userId: string) => {
      if (!roomId) return;
      await roomService.unbanMember(roomId, userId);
    },
    [roomId],
  );

  const invite = useCallback(
    async (username: string) => {
      if (!roomId) return;
      await roomInvitationService.invite(roomId, username);
    },
    [roomId],
  );

  const deleteRoom = useCallback(async () => {
    if (!roomId) return;
    await roomService.deleteRoom(roomId);
  }, [roomId]);

  return { kick, promote, demote, unban, invite, deleteRoom };
}
