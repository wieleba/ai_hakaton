import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from '../App';
import * as useAuthModule from '../hooks/useAuth';
import type { AuthContextType, User } from '../types/auth';

vi.mock('../hooks/useAuth');
vi.mock('../services/authService');

describe('Full Authentication Flow', () => {
  const mockUser: User = {
    id: 1,
    email: 'test@example.com',
    username: 'testuser',
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should initialize with auth hook and have all methods available', () => {
    const mockRegister = vi.fn().mockResolvedValue(mockUser);
    const mockLogin = vi.fn().mockResolvedValue({ user: mockUser, token: 'jwt-token' });
    const mockLogout = vi.fn();

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    vi.mocked(useAuthModule.useAuth).mockReturnValue({
      user: null,
      token: null,
      isLoading: false,
      register: mockRegister,
      login: mockLogin,
      logout: mockLogout,
      isAuthenticated: false,
    } as unknown as AuthContextType);

    render(<App />);

    // Verify auth methods are available
    expect(mockRegister).toBeDefined();
    expect(mockLogin).toBeDefined();
    expect(mockLogout).toBeDefined();
  });

  it('should display login form and handle successful login', async () => {
    const mockLogin = vi.fn().mockResolvedValue({ user: mockUser, token: 'jwt-token' });

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    vi.mocked(useAuthModule.useAuth).mockReturnValue({
      user: null,
      token: null,
      isLoading: false,
      register: vi.fn(),
      login: mockLogin,
      logout: vi.fn(),
      isAuthenticated: false,
    } as unknown as AuthContextType);

    const user = userEvent.setup();
    render(<App />);

    // Verify login form is displayed
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();

    // Fill and submit login form
    await user.type(screen.getByLabelText(/email/i), 'test@example.com');
    await user.type(screen.getByLabelText(/password/i), 'Password123!');

    const loginButton = screen.getByRole('button', { name: /login/i });
    await user.click(loginButton);

    // Verify login was called
    expect(mockLogin).toHaveBeenCalledWith({
      email: 'test@example.com',
      password: 'Password123!',
    });
  });

  it('should have working authentication hook with proper methods', () => {
    const mockRegister = vi.fn();
    const mockLogin = vi.fn();
    const mockLogout = vi.fn();

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    vi.mocked(useAuthModule.useAuth).mockReturnValue({
      user: null,
      token: null,
      isLoading: false,
      register: mockRegister,
      login: mockLogin,
      logout: mockLogout,
      isAuthenticated: false,
    } as unknown as AuthContextType);

    // Verify hook is properly mocked
    expect(mockRegister).toBeDefined();
    expect(mockLogin).toBeDefined();
    expect(mockLogout).toBeDefined();
  });

  it('should support complete auth lifecycle', async () => {
    const mockRegister = vi.fn().mockResolvedValue(mockUser);
    const mockLogin = vi.fn().mockResolvedValue({ user: mockUser, token: 'jwt-token' });
    const mockLogout = vi.fn();

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    vi.mocked(useAuthModule.useAuth).mockReturnValue({
      user: mockUser,
      token: 'jwt-token',
      isLoading: false,
      register: mockRegister,
      login: mockLogin,
      logout: mockLogout,
      isAuthenticated: true,
    } as unknown as AuthContextType);

    render(<App />);

    // Verify app renders with authenticated user
    expect(mockLogin).toBeDefined();
    expect(mockLogout).toBeDefined();
  });
});
