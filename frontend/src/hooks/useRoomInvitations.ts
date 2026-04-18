import { useCallback, useEffect, useState } from 'react';
import type { RoomInvitation } from '../types/roomModeration';
import { roomInvitationService } from '../services/roomInvitationService';

export function useRoomInvitations() {
  const [invitations, setInvitations] = useState<RoomInvitation[]>([]);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      setInvitations(await roomInvitationService.listMyIncoming());
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, []);

  useEffect(() => {
    reload();
  }, [reload]);

  const accept = useCallback(async (invitationId: string) => {
    await roomInvitationService.acceptInvitation(invitationId);
    setInvitations((prev) => prev.filter((i) => i.id !== invitationId));
  }, []);

  const decline = useCallback(async (invitationId: string) => {
    await roomInvitationService.declineInvitation(invitationId);
    setInvitations((prev) => prev.filter((i) => i.id !== invitationId));
  }, []);

  return { invitations, error, accept, decline, reload };
}
