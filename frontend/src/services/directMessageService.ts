import axios from 'axios';
import type { ConversationView, DirectConversation, DirectMessage } from '../types/directMessage';

export const directMessageService = {
  async listConversations(): Promise<ConversationView[]> {
    return (await axios.get('/api/dms/conversations')).data;
  },
  async getOrCreateWith(otherUserId: string): Promise<DirectConversation> {
    return (await axios.get(`/api/dms/with/${otherUserId}`)).data;
  },
  async getHistory(
    conversationId: string,
    before?: string,
    limit = 50,
  ): Promise<DirectMessage[]> {
    const params = new URLSearchParams();
    if (before) params.append('before', before);
    params.append('limit', String(limit));
    return (await axios.get(`/api/dms/${conversationId}/messages?${params}`)).data;
  },
  async sendMessage(
    conversationId: string,
    text: string,
    replyToId?: string,
  ): Promise<DirectMessage> {
    const body: Record<string, unknown> = { text };
    if (replyToId) body.replyToId = replyToId;
    return (await axios.post(`/api/dms/${conversationId}/messages`, body)).data;
  },
  async editMessage(
    conversationId: string,
    messageId: string,
    text: string,
  ): Promise<DirectMessage> {
    return (await axios.patch(`/api/dms/${conversationId}/messages/${messageId}`, { text })).data;
  },
  async deleteMessage(conversationId: string, messageId: string): Promise<void> {
    await axios.delete(`/api/dms/${conversationId}/messages/${messageId}`);
  },
};
