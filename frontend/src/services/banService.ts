import axios from 'axios';
import type { UserBan } from '../types/ban';

export const banService = {
  async banUser(userId: string): Promise<UserBan> {
    return (await axios.post('/api/bans', { userId })).data;
  },
  async listBans(): Promise<UserBan[]> {
    return (await axios.get('/api/bans')).data;
  },
};
