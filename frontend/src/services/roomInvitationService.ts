import axios from 'axios';
import type { RoomInvitation } from '../types/roomModeration';

export const roomInvitationService = {
  invite: async (roomId: string, username: string): Promise<void> => {
    await axios.post(`/api/rooms/${roomId}/invitations`, { username });
  },

  cancelInvitation: async (roomId: string, invitationId: string): Promise<void> => {
    await axios.delete(`/api/rooms/${roomId}/invitations/${invitationId}`);
  },

  listOutgoingForRoom: async (roomId: string): Promise<RoomInvitation[]> => {
    return (await axios.get(`/api/rooms/${roomId}/invitations`)).data;
  },

  listMyIncoming: async (): Promise<RoomInvitation[]> => {
    return (await axios.get('/api/invitations')).data;
  },

  acceptInvitation: async (invitationId: string): Promise<void> => {
    await axios.post(`/api/invitations/${invitationId}/accept`);
  },

  declineInvitation: async (invitationId: string): Promise<void> => {
    await axios.post(`/api/invitations/${invitationId}/decline`);
  },
};
