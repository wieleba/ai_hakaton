import type { Friendship } from './friendship';
import type { MessagePreview, ReactionSummary } from './room';
import type { AttachmentSummary } from './attachment';

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
  senderUsername?: string;
  text: string | null;
  createdAt: string;
  editedAt?: string | null;
  deletedAt?: string | null;
  deletedBy?: string | null;
  replyTo?: MessagePreview | null;
  reactions?: ReactionSummary[];
  attachment?: AttachmentSummary | null;
}

export type FriendEvent =
  | { type: 'REQUEST_CREATED'; friendship: Friendship }
  | { type: 'FRIENDSHIP_ACCEPTED'; counterpartUserId: string; friendship: Friendship }
  | { type: 'FRIENDSHIP_REMOVED'; counterpartUserId: string };
