export interface RoomHit {
  id: string;
  name: string;
  description: string | null;
  visibility: 'public' | 'private';
}

export interface UserHit {
  id: string;
  username: string;
}

export interface SearchResponse {
  rooms: RoomHit[];
  users: UserHit[];
}
