import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import ForgotPasswordPage from '../ForgotPasswordPage';
import { passwordResetService } from '../../services/passwordResetService';

vi.mock('../../services/passwordResetService', () => ({
  passwordResetService: { request: vi.fn().mockResolvedValue(undefined), confirm: vi.fn() },
}));

describe('ForgotPasswordPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('submits email and shows success message', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <ForgotPasswordPage />
      </MemoryRouter>,
    );

    await user.type(screen.getByLabelText(/email/i), 'alice@x.test');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));

    expect(passwordResetService.request).toHaveBeenCalledWith('alice@x.test');
    await waitFor(() =>
      expect(screen.getByText(/we sent a reset link/i)).toBeInTheDocument(),
    );
  });

  it('still shows success banner when backend errors (enumeration-safe)', async () => {
    (passwordResetService.request as ReturnType<typeof vi.fn>).mockRejectedValueOnce(
      new Error('boom'),
    );
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <ForgotPasswordPage />
      </MemoryRouter>,
    );

    await user.type(screen.getByLabelText(/email/i), 'alice@x.test');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));

    await waitFor(() =>
      expect(screen.getByText(/we sent a reset link/i)).toBeInTheDocument(),
    );
  });
});
