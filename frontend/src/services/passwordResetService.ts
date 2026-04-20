import axios from 'axios';

export const passwordResetService = {
  async request(email: string): Promise<void> {
    await axios.post('/api/password-reset/request', { email });
  },
  async confirm(token: string, newPassword: string): Promise<void> {
    await axios.post('/api/password-reset/confirm', { token, newPassword });
  },
};
