import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import ResetPasswordPage from '../ResetPasswordPage';
import { passwordResetService } from '../../services/passwordResetService';

vi.mock('../../services/passwordResetService', () => ({
  passwordResetService: { request: vi.fn(), confirm: vi.fn().mockResolvedValue(undefined) },
}));

function renderAt(url: string) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <ResetPasswordPage />
    </MemoryRouter>,
  );
}

describe('ResetPasswordPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows an error when token query param is missing', () => {
    renderAt('/reset-password');
    expect(screen.getByText(/missing its token/i)).toBeInTheDocument();
  });

  it('submits token + new password on save', async () => {
    const user = userEvent.setup();
    renderAt('/reset-password?token=abc');

    await user.type(screen.getByLabelText(/new password/i), 'newpass123');
    await user.type(screen.getByLabelText(/confirm password/i), 'newpass123');
    await user.click(screen.getByRole('button', { name: /save new password/i }));

    await waitFor(() =>
      expect(passwordResetService.confirm).toHaveBeenCalledWith('abc', 'newpass123'),
    );
  });

  it('shows mismatched-password error without calling the service', async () => {
    const user = userEvent.setup();
    renderAt('/reset-password?token=abc');

    await user.type(screen.getByLabelText(/new password/i), 'newpass123');
    await user.type(screen.getByLabelText(/confirm password/i), 'different1');
    await user.click(screen.getByRole('button', { name: /save new password/i }));

    expect(screen.getByText(/passwords do not match/i)).toBeInTheDocument();
    expect(passwordResetService.confirm).not.toHaveBeenCalled();
  });
});
