import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import AuthGuard from '../AuthGuard';
import * as useAuthModule from '../../hooks/useAuth';
import type { AuthContextType } from '../../types/auth';

vi.mock('../../hooks/useAuth');

describe('AuthGuard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should render children when user is authenticated', () => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    vi.mocked(useAuthModule.useAuth).mockReturnValue({
      isAuthenticated: true,
      user: { id: 1, email: 'test@example.com', username: 'testuser' },
      token: 'jwt-token',
      isLoading: false,
    } as unknown as AuthContextType);

    render(
      <MemoryRouter>
        <AuthGuard>
          <div>Protected Content</div>
        </AuthGuard>
      </MemoryRouter>
    );

    expect(screen.getByText('Protected Content')).toBeInTheDocument();
  });

  it('should redirect to login when user is not authenticated', () => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    vi.mocked(useAuthModule.useAuth).mockReturnValue({
      isAuthenticated: false,
      user: null,
      token: null,
      isLoading: false,
    } as unknown as AuthContextType);

    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <AuthGuard>
          <div>Protected Content</div>
        </AuthGuard>
      </MemoryRouter>
    );

    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
  });

  it('should show loading state while checking authentication', () => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    vi.mocked(useAuthModule.useAuth).mockReturnValue({
      isAuthenticated: false,
      user: null,
      token: null,
      isLoading: true,
    } as unknown as AuthContextType);

    render(
      <MemoryRouter>
        <AuthGuard>
          <div>Protected Content</div>
        </AuthGuard>
      </MemoryRouter>
    );

    expect(screen.getByText('Loading...')).toBeInTheDocument();
  });
});
