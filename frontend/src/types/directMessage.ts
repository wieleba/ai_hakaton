import type { Friendship } from './friendship';

export interface DirectConversation {
  id: string;
  user1Id: string;
  user2Id: string;
  createdAt: string;
}

export interface ConversationView {
  id: string;
  otherUserId: string;
  otherUsername: string;
  lastMessage: string | null;
  lastMessageAt: string | null;
}

export interface DirectMessage {
  id: string;
  conversationId: string;
  senderId: string;
  text: string;
  createdAt: string;
}

export type FriendEvent =
  | { type: 'REQUEST_CREATED'; friendship: Friendship }
  | { type: 'FRIENDSHIP_ACCEPTED'; counterpartUserId: string; friendship: Friendship }
  | { type: 'FRIENDSHIP_REMOVED'; counterpartUserId: string };
