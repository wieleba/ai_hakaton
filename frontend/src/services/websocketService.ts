import SockJS from 'sockjs-client';
import * as Stomp from 'stompjs';

export interface WebSocketMessage {
  [key: string]: unknown;
}

export interface Subscription {
  unsubscribe: () => void;
}

type FrameHandler = (frame: any) => void;

class WebSocketService {
  private client: Stomp.Client | null = null;
  private subscriptions: Map<string, Subscription> = new Map();

  connect(token?: string): Promise<void> {
    return new Promise((resolve, reject) => {
      const socket = new SockJS('/ws/chat');
      this.client = Stomp.over(socket);

      this.client.connect(
        {
          ...(token && { Authorization: `Bearer ${token}` }),
        },
        () => {
          resolve();
        },
        (error: any) => {
          reject(new Error(`WebSocket connection failed: ${error}`));
        }
      );
    });
  }

  disconnect(): void {
    if (this.client && this.client.connected) {
      this.subscriptions.forEach((sub) => sub.unsubscribe());
      this.subscriptions.clear();
      this.client.disconnect(() => {
        this.client = null;
      });
    }
  }

  subscribe(
    destination: string,
    callback: (message: WebSocketMessage) => void
  ): Subscription {
    if (!this.client || !this.client.connected) {
      throw new Error('WebSocket not connected');
    }

    const subscription = this.client.subscribe(destination, (frame: any) => {
      try {
        const body = JSON.parse(frame.body);
        callback(body);
      } catch (error) {
        console.error('Failed to parse WebSocket message:', error);
        callback(frame.body);
      }
    });

    const wrappedSubscription: Subscription = {
      unsubscribe: () => {
        subscription.unsubscribe();
        this.subscriptions.delete(destination);
      },
    };

    this.subscriptions.set(destination, wrappedSubscription);
    return wrappedSubscription;
  }

  send(destination: string, body: WebSocketMessage): void {
    if (!this.client || !this.client.connected) {
      throw new Error('WebSocket not connected');
    }
    this.client.send(destination, {}, JSON.stringify(body));
  }

  isConnected(): boolean {
    return this.client?.connected ?? false;
  }
}

export const websocketService = new WebSocketService();
