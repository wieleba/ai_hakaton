import React from 'react';
import { useMatch } from 'react-router-dom';
import { RoomMembersPanel } from '../components/RoomMembersPanel';

const getCurrentUserId = (): string | null => {
  const token = localStorage.getItem('authToken');
  if (!token) return null;
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return typeof payload.sub === 'string' ? payload.sub : null;
  } catch {
    return null;
  }
};

export const RightPanel: React.FC = () => {
  const roomMatch = useMatch('/rooms/:roomId');
  const roomId = roomMatch?.params.roomId;
  const currentUserId = getCurrentUserId();

  if (roomId && currentUserId) {
    return <RoomMembersPanel roomId={roomId} currentUserId={currentUserId} />;
  }
  return null;
};
