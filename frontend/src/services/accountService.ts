import axios from 'axios';

export const accountService = {
  async changePassword(oldPassword: string, newPassword: string): Promise<void> {
    await axios.patch('/api/users/me/password', { oldPassword, newPassword });
  },

  async deleteAccount(): Promise<void> {
    await axios.delete('/api/users/me');
  },
};
