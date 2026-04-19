import axios from 'axios';
import type { Message } from '../types/room';

export const messageService = {
  async getHistory(roomId: string, before?: string, limit = 50): Promise<Message[]> {
    const params = new URLSearchParams();
    if (before) params.append('before', before);
    params.append('limit', String(limit));
    return (await axios.get(`/api/rooms/${roomId}/messages?${params}`)).data;
  },

  async sendMessage(
    roomId: string,
    text: string,
    replyToId?: string,
  ): Promise<Message> {
    const body: Record<string, unknown> = { text };
    if (replyToId) body.replyToId = replyToId;
    return (await axios.post(`/api/rooms/${roomId}/messages`, body)).data;
  },

  async editMessage(roomId: string, messageId: string, text: string): Promise<Message> {
    return (await axios.patch(`/api/rooms/${roomId}/messages/${messageId}`, { text })).data;
  },

  async deleteMessage(roomId: string, messageId: string): Promise<void> {
    await axios.delete(`/api/rooms/${roomId}/messages/${messageId}`);
  },

  async toggleReaction(
    roomId: string,
    messageId: string,
    emoji: string,
  ): Promise<Message> {
    return (await axios.post(`/api/rooms/${roomId}/messages/${messageId}/reactions`, { emoji })).data;
  },

  async sendMessageWithAttachment(
    roomId: string,
    text: string,
    file: File,
    replyToId?: string,
  ): Promise<Message> {
    const form = new FormData();
    if (text.trim().length > 0) form.append('text', text);
    if (replyToId) form.append('replyToId', replyToId);
    form.append('file', file);
    const response = await axios.post(`/api/rooms/${roomId}/messages`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data;
  },
};
