export interface SessionRow {
  sessionId: string;
  userAgent: string | null;
  remoteAddr: string | null;
  connectedAt: string;
  lastSeen: string;
  idle: boolean;
  current: boolean;
}

export interface LogoutOthersResponse {
  revokedCount: number;
}
