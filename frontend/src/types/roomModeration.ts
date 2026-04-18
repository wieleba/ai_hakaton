import type { ChatRoom } from './room';

export type RoomRole = 'member' | 'admin';

export interface RoomMemberView {
  userId: string;
  username: string;
  role: RoomRole;
  isOwner: boolean;
}

export interface RoomBan {
  bannedUserId: string;
  bannedUsername: string;
  bannedById: string;
  bannedByUsername: string;
  bannedAt: string;
}

export interface RoomInvitation {
  id: string;
  roomId: string;
  roomName: string;
  inviterId: string;
  inviterUsername: string;
  createdAt: string;
}

export type { ChatRoom };
