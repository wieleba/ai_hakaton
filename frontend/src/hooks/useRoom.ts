import { useState, useCallback } from 'react';
import { ChatRoom } from '../types/room';
import { roomService } from '../services/roomService';

export const useRoom = () => {
  const [currentRoom, setCurrentRoom] = useState<ChatRoom | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchRoom = useCallback(async (roomId: string) => {
    setIsLoading(true);
    setError(null);
    try {
      const room = await roomService.getRoomById(roomId);
      setCurrentRoom(room);
    } catch (err: any) {
      setError(err.message);
    } finally {
      setIsLoading(false);
    }
  }, []);

  const joinRoom = useCallback(async (roomId: string) => {
    setIsLoading(true);
    setError(null);
    try {
      await roomService.joinRoom(roomId);
      await fetchRoom(roomId);
    } catch (err: any) {
      setError(err.message);
    } finally {
      setIsLoading(false);
    }
  }, [fetchRoom]);

  const leaveRoom = useCallback(async (roomId: string) => {
    setIsLoading(true);
    setError(null);
    try {
      await roomService.leaveRoom(roomId);
      setCurrentRoom(null);
    } catch (err: any) {
      setError(err.message);
    } finally {
      setIsLoading(false);
    }
  }, []);

  return { currentRoom, isLoading, error, fetchRoom, joinRoom, leaveRoom };
};
