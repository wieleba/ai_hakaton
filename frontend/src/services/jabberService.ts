import axios from 'axios';

export interface JabberServerStatus {
  label: string;
  domain: string;
  host: string;
  clientPort: number;
  s2sPort: number;
  clientReachable: boolean;
  s2sReachable: boolean;
  httpApiReachable: boolean;
  registeredUsers: number | null;
  onlineUsers: number | null;
  outgoingS2sConnections: number | null;
  incomingS2sConnections: number | null;
}

export interface MyJabberCredentials {
  available: boolean;
  jid: string;
  host: string | null;
  port: number | null;
  password: string | null;
}

export const jabberService = {
  async getStatus(): Promise<JabberServerStatus[]> {
    const response = await axios.get<JabberServerStatus[]>('/api/jabber/status');
    return response.data;
  },

  async getMyCredentials(): Promise<MyJabberCredentials> {
    const response = await axios.get<MyJabberCredentials>('/api/jabber/me');
    return response.data;
  },
};
