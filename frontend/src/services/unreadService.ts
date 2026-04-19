import axios from 'axios';
import type { UnreadCountsResponse } from '../types/unread';

export const unreadService = {
  async counts(): Promise<UnreadCountsResponse> {
    return (await axios.get<UnreadCountsResponse>('/api/unread-counts')).data;
  },
  async markRoomRead(roomId: string): Promise<void> {
    await axios.post(`/api/rooms/${encodeURIComponent(roomId)}/read`);
  },
  async markDmRead(conversationId: string): Promise<void> {
    await axios.post(`/api/dms/${encodeURIComponent(conversationId)}/read`);
  },
};
