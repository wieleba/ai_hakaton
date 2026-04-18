import axios from 'axios';
import { ChatRoom } from '../types/room';

const API_BASE = '/api/rooms';

export const roomService = {
  createRoom: async (name: string, description?: string): Promise<ChatRoom> => {
    const response = await axios.post(`${API_BASE}`, { name, description });
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

  joinRoom: async (roomId: string): Promise<void> => {
    await axios.post(`${API_BASE}/${roomId}/join`);
  },

  leaveRoom: async (roomId: string): Promise<void> => {
    await axios.post(`${API_BASE}/${roomId}/leave`);
  },

  getRoomById: async (roomId: string): Promise<ChatRoom> => {
    const response = await axios.get(`${API_BASE}/${roomId}`);
    return response.data;
  },

  listMembers: async (roomId: string): Promise<{ userId: string; username: string }[]> => {
    return (await axios.get(`${API_BASE}/${roomId}/members`)).data;
  },
};
