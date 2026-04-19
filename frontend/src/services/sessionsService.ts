import axios from 'axios';
import type { SessionRow, LogoutOthersResponse } from '../types/session';

export const sessionsService = {
  async list(): Promise<SessionRow[]> {
    return (await axios.get<SessionRow[]>('/api/sessions')).data;
  },
  async logout(sessionId: string): Promise<void> {
    await axios.delete(`/api/sessions/${encodeURIComponent(sessionId)}`);
  },
  async logoutOthers(): Promise<LogoutOthersResponse> {
    return (await axios.delete<LogoutOthersResponse>('/api/sessions/others')).data;
  },
};
