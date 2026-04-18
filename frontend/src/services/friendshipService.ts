import axios from 'axios';
import type { Friendship, FriendView } from '../types/friendship';

const BASE = '/api/friendships';

export const friendshipService = {
  async sendRequest(username: string): Promise<Friendship> {
    return (await axios.post(`${BASE}/requests`, { username })).data;
  },
  async listIncoming(): Promise<Friendship[]> {
    return (await axios.get(`${BASE}/requests?direction=incoming`)).data;
  },
  async listOutgoing(): Promise<Friendship[]> {
    return (await axios.get(`${BASE}/requests?direction=outgoing`)).data;
  },
  async accept(requestId: string): Promise<Friendship> {
    return (await axios.post(`${BASE}/requests/${requestId}/accept`)).data;
  },
  async reject(requestId: string): Promise<void> {
    await axios.post(`${BASE}/requests/${requestId}/reject`);
  },
  async cancel(requestId: string): Promise<void> {
    await axios.post(`${BASE}/requests/${requestId}/cancel`);
  },
  async listFriends(): Promise<FriendView[]> {
    return (await axios.get(BASE)).data;
  },
  async removeFriend(friendUserId: string): Promise<void> {
    await axios.delete(`${BASE}/${friendUserId}`);
  },
};
