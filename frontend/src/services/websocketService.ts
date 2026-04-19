import SockJS from 'sockjs-client';
import * as Stomp from 'stompjs';

export interface WebSocketMessage {
  [key: string]: unknown;
}

export interface Subscription {
  unsubscribe: () => void;
}

class WebSocketService {
  private client: Stomp.Client | null = null;
  private pendingConnection: Promise<void> | null = null;
  private subscriptions: Map<string, Subscription> = new Map();
  private sessionId: string | null = null;

  connect(token?: string): Promise<void> {
    if (this.client && this.client.connected) return Promise.resolve();
    if (this.pendingConnection) return this.pendingConnection;

    this.pendingConnection = new Promise<void>((resolve, reject) => {
      const socket = new SockJS('/ws/chat');
      const client = Stomp.over(socket);
      client.debug = () => {};
      this.client = client;

      client.connect(
        {
          ...(token && { Authorization: `Bearer ${token}` }),
        },
        (frame?: { headers?: Record<string, string> }) => {
          this.pendingConnection = null;
          this.sessionId = frame?.headers?.session ?? null;
          resolve();
        },
        (error: unknown) => {
          this.pendingConnection = null;
          this.sessionId = null;
          if (this.client === client) this.client = null;
          reject(new Error(`WebSocket connection failed: ${String(error)}`));
        },
      );
    });

    return this.pendingConnection;
  }

  disconnect(): void {
    const client = this.client;
    if (!client) return;

    this.subscriptions.forEach((sub) => sub.unsubscribe());
    this.subscriptions.clear();

    if (client.connected) {
      client.disconnect(() => {
        if (this.client === client) this.client = null;
        this.sessionId = null;
      });
    } else {
      if (this.client === client) this.client = null;
      this.sessionId = null;
    }
  }

  subscribe(
    destination: string,
    callback: (message: WebSocketMessage) => void,
  ): Subscription {
    if (!this.client || !this.client.connected) {
      throw new Error('WebSocket not connected');
    }
    const existing = this.subscriptions.get(destination);
    if (existing) existing.unsubscribe();

    const raw = this.client.subscribe(destination, (frame: { body: string }) => {
      try {
        const body = JSON.parse(frame.body) as WebSocketMessage;
        callback(body);
      } catch (error) {
        console.error('Failed to parse WebSocket message:', error);
        callback({ raw: frame.body });
      }
    });

    const wrapped: Subscription = {
      unsubscribe: () => {
        raw.unsubscribe();
        if (this.subscriptions.get(destination) === wrapped) {
          this.subscriptions.delete(destination);
        }
      },
    };

    this.subscriptions.set(destination, wrapped);
    return wrapped;
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

  getSessionId(): string | null {
    return this.sessionId;
  }
}

export const websocketService = new WebSocketService();
