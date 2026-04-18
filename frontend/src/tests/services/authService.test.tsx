import { describe, it, expect, vi, beforeEach } from 'vitest';
import axios from 'axios';
import { authService } from '../../services/authService';

vi.mock('axios');

describe('authService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should register a user successfully', async () => {
    const mockUser = { id: 1, email: 'test@example.com', username: 'testuser' };
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    vi.mocked(axios.post).mockResolvedValueOnce({ data: { user: mockUser } } as any);

    const result = await authService.register({
      email: 'test@example.com',
      username: 'testuser',
      password: 'password123',
    });

    expect(result).toEqual(mockUser);
    expect(axios.post).toHaveBeenCalledWith('/api/users/register', {
      email: 'test@example.com',
      username: 'testuser',
      password: 'password123',
    });
  });

  it('should login a user successfully', async () => {
    const mockResponse = {
      token: 'jwt-token',
      user: { id: 1, email: 'test@example.com', username: 'testuser' },
    };
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (axios.post as any).mockResolvedValueOnce({ data: mockResponse });

    const result = await authService.login({
      email: 'test@example.com',
      password: 'password123',
    });

    expect(result).toEqual(mockResponse);
    expect(axios.post).toHaveBeenCalledWith('/api/users/login', {
      email: 'test@example.com',
      password: 'password123',
    });
  });

  it('should handle registration errors', async () => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (axios.post as any).mockRejectedValueOnce(new Error('Email already exists'));

    await expect(
      authService.register({
        email: 'existing@example.com',
        username: 'testuser',
        password: 'password123',
      })
    ).rejects.toThrow();
  });

  it('should handle login errors', async () => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (axios.post as any).mockRejectedValueOnce(new Error('Invalid credentials'));

    await expect(
      authService.login({
        email: 'test@example.com',
        password: 'wrongpassword',
      })
    ).rejects.toThrow();
  });

  it('should get current user', async () => {
    const mockUser = { id: 1, email: 'test@example.com', username: 'testuser' };
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (axios.get as any).mockResolvedValueOnce({ data: mockUser });

    const result = await authService.getCurrentUser('jwt-token');

    expect(result).toEqual(mockUser);
    expect(axios.get).toHaveBeenCalledWith('/api/users/me', {
      headers: { Authorization: 'Bearer jwt-token' },
    });
  });

  it('should logout', async () => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (axios.post as any).mockResolvedValueOnce({ data: {} });

    await authService.logout('jwt-token');

    expect(axios.post).toHaveBeenCalledWith(
      '/api/users/logout',
      {},
      {
        headers: { Authorization: 'Bearer jwt-token' },
      }
    );
  });
});
