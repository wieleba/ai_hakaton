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

export interface Message {
  id: string;
  roomId: string;
  userId: string;
  username: string;
  text: string;
  createdAt: string;
}

export interface RoomMember {
  id: string;
  roomId: string;
  userId: string;
  joinedAt: string;
}
