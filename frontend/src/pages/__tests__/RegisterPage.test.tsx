import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import userEvent from '@testing-library/user-event';
import RegisterPage from '../RegisterPage';

const renderRegister = () =>
  render(
    <MemoryRouter>
      <RegisterPage />
    </MemoryRouter>,
  );

vi.mock('../../services/authService');
vi.mock('../../hooks/useAuth', () => ({
  useAuth: () => ({
    register: vi.fn().mockResolvedValue({ id: 1, email: 'test@example.com', username: 'testuser' }),
    isLoading: false,
  }),
}));
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => vi.fn() };
});

describe('RegisterPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should render registration form with email, username, password, and confirm password fields', () => {
    renderRegister();

    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/username/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^password/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/confirm password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /register/i })).toBeInTheDocument();
  });

  it('should call register with form data when form is submitted with matching passwords', async () => {
    const user = userEvent.setup();
    renderRegister();

    await user.type(screen.getByLabelText(/email/i), 'test@example.com');
    await user.type(screen.getByLabelText(/username/i), 'testuser');
    await user.type(screen.getByLabelText(/^password/i), 'Password123!');
    await user.type(screen.getByLabelText(/confirm password/i), 'Password123!');

    const registerButton = screen.getByRole('button', { name: /register/i });
    await user.click(registerButton);

    expect(screen.getByText(/registration successful/i)).toBeInTheDocument();
  });

  it('should show error when passwords do not match', async () => {
    const user = userEvent.setup();
    renderRegister();

    await user.type(screen.getByLabelText(/email/i), 'test@example.com');
    await user.type(screen.getByLabelText(/username/i), 'testuser');
    await user.type(screen.getByLabelText(/^password/i), 'Password123!');
    await user.type(screen.getByLabelText(/confirm password/i), 'DifferentPassword');

    const registerButton = screen.getByRole('button', { name: /register/i });
    await user.click(registerButton);

    expect(screen.getByText(/passwords do not match/i)).toBeInTheDocument();
  });
});
