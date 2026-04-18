export interface User {
  id: string;
  username: string;
  email: string;
}

export interface ChatRoom {
  id: string;
  name: string;
  description?: string;
  ownerId: string;
  visibility: 'public' | 'private';
  createdAt: string;
  updatedAt: string;
}

export interface MessagePreview {
  id: string;
  authorUsername: string;
  textPreview: string;
}

export interface ReactionSummary {
  emoji: string;
  count: number;
  reactedByMe: boolean;
}

export interface Message {
  id: string;
  roomId: string;
  userId: string;
  username: string;
  text: string | null;
  createdAt: string;
  editedAt?: string | null;
  deletedAt?: string | null;
  deletedBy?: string | null;
  replyTo?: MessagePreview | null;
  reactions?: ReactionSummary[];
}

export interface RoomMember {
  id: string;
  roomId: string;
  userId: string;
  joinedAt: string;
}
