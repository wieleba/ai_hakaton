export interface UnreadCountsResponse {
  rooms: Record<string, number>;
  dms: Record<string, number>;
}

export interface UnreadBump {
  chatType: 'ROOM' | 'DM';
  chatId: string;
}
