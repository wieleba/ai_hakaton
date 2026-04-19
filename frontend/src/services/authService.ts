import axios from 'axios';
import { User, AuthResponse, RegisterPayload, LoginPayload } from '../types/auth';
import { websocketService } from './websocketService';

const API_URL = '/api/users';

// Attach Bearer token + STOMP session id to every outgoing request.
// Bearer is needed because full-page reloads lose the in-memory axios.defaults header
// set by setAuthToken at login; X-Session-Id is consumed by the sessions-management
// endpoints to identify the calling tab.
axios.interceptors.request.use((config) => {
  const token = localStorage.getItem('authToken');
  if (token && config.headers) {
    config.headers['Authorization'] = `Bearer ${token}`;
  }
  const sid = websocketService.getSessionId();
  if (sid && config.headers) {
    config.headers['X-Session-Id'] = sid;
  }
  return config;
});

export const authService = {
  async register(payload: RegisterPayload): Promise<User> {
    const response = await axios.post(`${API_URL}/register`, payload);
    return response.data.user || response.data;
  },

  async login(payload: LoginPayload): Promise<AuthResponse> {
    const response = await axios.post(`${API_URL}/login`, payload);
    return response.data;
  },

  async getCurrentUser(token: string): Promise<User> {
    const response = await axios.get(`${API_URL}/me`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    return response.data;
  },

  async logout(token: string): Promise<void> {
    await axios.post(
      `${API_URL}/logout`,
      {},
      {
        headers: { Authorization: `Bearer ${token}` },
      }
    );
  },

  setAuthToken(token: string): void {
    if (token) {
      axios.defaults.headers.common['Authorization'] = `Bearer ${token}`;
      localStorage.setItem('authToken', token);
    }
  },

  removeAuthToken(): void {
    delete axios.defaults.headers.common['Authorization'];
    localStorage.removeItem('authToken');
  },

  getStoredToken(): string | null {
    return localStorage.getItem('authToken');
  },
};
