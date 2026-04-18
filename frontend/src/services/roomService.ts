import axios from 'axios';
import { ChatRoom } from '../types/room';
import type { RoomBan, RoomMemberView } from '../types/roomModeration';

const API_BASE = '/api/rooms';

export const roomService = {
  createRoom: async (
    name: string,
    description?: string,
    visibility?: 'public' | 'private',
  ): Promise<ChatRoom> => {
    const body: Record<string, unknown> = { name };
    if (description !== undefined) body.description = description;
    if (visibility !== undefined) body.visibility = visibility;
    const response = await axios.post(`${API_BASE}`, body);
    return response.data;
  },

  listPublicRooms: async (page: number = 0, limit: number = 20): Promise<{
    content: ChatRoom[];
    totalElements: number;
    totalPages: number;
  }> => {
    const response = await axios.get(`${API_BASE}?page=${page}&limit=${limit}`);
    return response.data;
  },

  listMyRooms: async (): Promise<ChatRoom[]> => {
    return (await axios.get(`${API_BASE}/mine`)).data;
  },

  joinRoom: async (roomId: string): Promise<void> => {
    await axios.post(`${API_BASE}/${roomId}/join`);
  },

  leaveRoom: async (roomId: string): Promise<void> => {
    await axios.post(`${API_BASE}/${roomId}/leave`);
  },

  deleteRoom: async (roomId: string): Promise<void> => {
    await axios.delete(`${API_BASE}/${roomId}`);
  },

  getRoomById: async (roomId: string): Promise<ChatRoom> => {
    const response = await axios.get(`${API_BASE}/${roomId}`);
    return response.data;
  },

  listMembers: async (roomId: string): Promise<{ userId: string; username: string }[]> => {
    return (await axios.get(`${API_BASE}/${roomId}/members`)).data;
  },

  listMembersWithRole: async (roomId: string): Promise<RoomMemberView[]> => {
    return (await axios.get(`${API_BASE}/${roomId}/members`)).data;
  },

  kickMember: async (roomId: string, userId: string): Promise<void> => {
    await axios.delete(`${API_BASE}/${roomId}/members/${userId}`);
  },

  promoteAdmin: async (roomId: string, userId: string): Promise<void> => {
    await axios.post(`${API_BASE}/${roomId}/admins`, { userId });
  },

  demoteAdmin: async (roomId: string, userId: string): Promise<void> => {
    await axios.delete(`${API_BASE}/${roomId}/admins/${userId}`);
  },

  listBans: async (roomId: string): Promise<RoomBan[]> => {
    return (await axios.get(`${API_BASE}/${roomId}/bans`)).data;
  },

  unbanMember: async (roomId: string, userId: string): Promise<void> => {
    await axios.delete(`${API_BASE}/${roomId}/bans/${userId}`);
  },
};
