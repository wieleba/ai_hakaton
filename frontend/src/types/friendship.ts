export type FriendshipStatus = 'pending' | 'accepted';

export interface Friendship {
  id: string;
  requesterId: string;
  addresseeId: string;
  status: FriendshipStatus;
  createdAt: string;
  updatedAt: string;
}

export interface FriendView {
  friendshipId: string;
  userId: string;
  username: string;
}
