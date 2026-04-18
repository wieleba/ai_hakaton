import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import userEvent from '@testing-library/user-event';
import LoginPage from '../LoginPage';

const renderLogin = () =>
  render(
    <MemoryRouter>
      <LoginPage />
    </MemoryRouter>,
  );
import * as useAuthModule from '../../hooks/useAuth';
import type { AuthContextType } from '../../types/auth';

vi.mock('../../services/authService');
vi.mock('../../hooks/useAuth');
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => vi.fn() };
});

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should render login form with email and password fields', () => {
    vi.mocked(useAuthModule.useAuth).mockReturnValue({
      login: vi.fn().mockResolvedValue({ id: 1, email: 'test@example.com', username: 'testuser' }),
      isLoading: false,
    } as unknown as AuthContextType); // eslint-disable-line @typescript-eslint/no-explicit-any

    renderLogin();

    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /login/i })).toBeInTheDocument();
  });

  it('should call login with credentials when form is submitted', async () => {
    vi.mocked(useAuthModule.useAuth).mockReturnValue({
      login: vi.fn().mockResolvedValue({ id: 1, email: 'test@example.com', username: 'testuser' }),
      isLoading: false,
    } as unknown as AuthContextType); // eslint-disable-line @typescript-eslint/no-explicit-any

    const user = userEvent.setup();
    renderLogin();

    await user.type(screen.getByLabelText(/email/i), 'test@example.com');
    await user.type(screen.getByLabelText(/password/i), 'Password123!');

    const loginButton = screen.getByRole('button', { name: /login/i });
    await user.click(loginButton);

    expect(screen.getByText(/login successful/i)).toBeInTheDocument();
  });

  it('should show error message on login failure', async () => {
    const mockLogin = vi.fn().mockRejectedValue(new Error('Invalid credentials'));
    vi.mocked(useAuthModule.useAuth).mockReturnValue({
      login: mockLogin,
      isLoading: false,
    } as unknown as AuthContextType); // eslint-disable-line @typescript-eslint/no-explicit-any

    const user = userEvent.setup();
    renderLogin();

    await user.type(screen.getByLabelText(/email/i), 'wrong@example.com');
    await user.type(screen.getByLabelText(/password/i), 'WrongPassword');

    const loginButton = screen.getByRole('button', { name: /login/i });
    await user.click(loginButton);

    expect(screen.getByText(/invalid credentials/i)).toBeInTheDocument();
  });
});
