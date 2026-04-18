import { useState, useEffect, useCallback } from 'react';
import { websocketService } from '../services/websocketService';
import { Message } from '../types/room';

export const useWebSocket = () => {
  const [isConnected, setIsConnected] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const subscriptions = new Map<string, any>();

  useEffect(() => {
    const connect = async () => {
      try {
        const token = localStorage.getItem('authToken') || '';
        await websocketService.connect(token);
        setIsConnected(true);
      } catch (err: any) {
        setError(err.message);
      }
    };
    connect();
    return () => {
      websocketService.disconnect();
      setIsConnected(false);
    };
  }, []);

  const subscribe = useCallback((roomId: string, onMessage: (message: Message) => void) => {
    if (!websocketService.isConnected()) {
      console.warn('WebSocket not connected');
      return;
    }
    const subscription = websocketService.subscribe(`/topic/room/${roomId}`, (message) => {
      onMessage(message as unknown as Message);
    });
    subscriptions.set(roomId, subscription);
  }, []);

  const unsubscribe = useCallback((roomId: string) => {
    const subscription = subscriptions.get(roomId);
    if (subscription) {
      subscription.unsubscribe();
      subscriptions.delete(roomId);
    }
  }, []);

  const sendMessage = useCallback((roomId: string, text: string) => {
    if (!websocketService.isConnected()) {
      throw new Error('WebSocket not connected');
    }
    websocketService.send(`/app/rooms/${roomId}/message`, { text });
  }, []);

  return { isConnected, error, subscribe, unsubscribe, sendMessage };
};
