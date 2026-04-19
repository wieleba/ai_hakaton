import axios from 'axios';
import type { PresenceState } from '../types/presence';

export const presenceService = {
  async snapshot(userIds: string[]): Promise<Record<string, PresenceState>> {
    if (userIds.length === 0) return {};
    const params = new URLSearchParams();
    for (const id of userIds) params.append('userIds', id);
    return (await axios.get(`/api/presence?${params.toString()}`)).data;
  },
};
