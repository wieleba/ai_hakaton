import axios from 'axios';
import { Message } from '../types/room';

export const messageService = {
  getMessageHistory: async (
    roomId: string,
    before?: string,
    limit: number = 50
  ): Promise<Message[]> => {
    const params = new URLSearchParams();
    if (before) params.append('before', before);
    params.append('limit', limit.toString());

    const response = await axios.get(
      `/api/rooms/${roomId}/messages?${params.toString()}`
    );
    return response.data;
  },

  sendMessage: async (roomId: string, text: string): Promise<Message> => {
    const response = await axios.post(`/api/rooms/${roomId}/messages`, { text });
    return response.data;
  },
};
